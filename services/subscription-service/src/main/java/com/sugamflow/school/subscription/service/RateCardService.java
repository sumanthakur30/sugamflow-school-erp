package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.pricing.RateCardMath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Standard feature rates, plan bundle discounts, and customer quotes.
 * Existing plan prices, entitlements, and tenant subscriptions are not rewritten.
 */
@Service
public class RateCardService {

  static final Set<String> UNIT_MODELS =
      Set.of(
          "PER_ORGANIZATION",
          "PER_OUTLET",
          "PER_USER",
          "PER_EMPLOYEE",
          "PER_WAREHOUSE",
          "PER_LOCATION",
          "PER_CHANNEL",
          "PER_TRANSACTION",
          "USAGE",
          "ONE_TIME",
          "ANNUAL");

  private static final String RATE_SELECT =
      """
      SELECT r.id, r.feature_code, f.name AS feature_name, f.module_code,
             r.business_type_code, r.unit_model, r.monthly_amount_minor, r.yearly_amount_minor,
             r.gst_inclusive, r.sellable_addon, r.effective_from, r.effective_until,
             r.changed_by, r.change_reason, r.benchmark_low_minor, r.benchmark_average_minor,
             r.benchmark_high_minor, r.benchmark_notes, r.benchmark_reviewed_on
      FROM feature_rate_version r
      JOIN feature_definition f ON f.code = r.feature_code
      """;

  private final JdbcTemplate jdbc;

  public RateCardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listRates(boolean openOnly, boolean addonsOnly) {
    String sql = RATE_SELECT + " WHERE 1=1";
    if (openOnly) {
      sql += " AND r.effective_until IS NULL";
    }
    if (addonsOnly) {
      sql += " AND r.sellable_addon = TRUE";
    }
    sql += " ORDER BY f.name, r.business_type_code NULLS FIRST, r.effective_from DESC";
    return jdbc.query(sql, (rs, row) -> rate(rs));
  }

  @Transactional
  public Map<String, Object> reviseRate(Map<String, Object> body) {
    String featureCode = required(body, "featureCode");
    Integer features =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM feature_definition WHERE code = ?", Integer.class, featureCode);
    if (features == null || features == 0) {
      throw new IllegalArgumentException("Unknown feature: " + featureCode);
    }
    String businessType = blankToNull(text(body, "businessTypeCode"));
    if (businessType != null) {
      businessType = businessType.toUpperCase(Locale.ROOT);
    }
    String unit = text(body, "unitModel");
    if (!StringUtils.hasText(unit)) {
      unit = "PER_ORGANIZATION";
    }
    unit = unit.trim().toUpperCase(Locale.ROOT);
    if (!UNIT_MODELS.contains(unit)) {
      throw new IllegalArgumentException("Unknown unit model: " + unit);
    }
    long monthly = nonNegative(body.get("monthlyAmountMinor"), "monthlyAmountMinor");
    long yearly = nonNegative(body.get("yearlyAmountMinor"), "yearlyAmountMinor");
    boolean gstInclusive = bool(body.get("gstInclusive"));
    boolean addon = bool(body.get("sellableAddon"));
    String changedBy = blankToNull(text(body, "changedBy"));
    String reason = blankToNull(text(body, "changeReason"));
    if (!StringUtils.hasText(reason)) {
      throw new IllegalArgumentException("changeReason is required");
    }
    jdbc.update(
        """
        UPDATE feature_rate_version
           SET effective_until = NOW()
         WHERE feature_code = ?
           AND effective_until IS NULL
           AND business_type_code IS NOT DISTINCT FROM ?
        """,
        featureCode,
        businessType);
    Long id =
        jdbc.queryForObject(
            """
            INSERT INTO feature_rate_version (
              feature_code, business_type_code, unit_model, monthly_amount_minor, yearly_amount_minor,
              gst_inclusive, sellable_addon, changed_by, change_reason,
              benchmark_low_minor, benchmark_average_minor, benchmark_high_minor,
              benchmark_notes, benchmark_reviewed_on
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS DATE))
            RETURNING id
            """,
            Long.class,
            featureCode,
            businessType,
            unit,
            monthly,
            yearly,
            gstInclusive,
            addon,
            changedBy,
            reason,
            optionalLong(body.get("benchmarkLowMinor")),
            optionalLong(body.get("benchmarkAverageMinor")),
            optionalLong(body.get("benchmarkHighMinor")),
            blankToNull(text(body, "benchmarkNotes")),
            blankToNull(text(body, "benchmarkReviewedOn")));
    return Map.of("id", id, "featureCode", featureCode, "businessTypeCode", businessType == null ? "" : businessType);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> valuePlan(String planId, String businessType, String cycle) {
    String type = blankToNull(businessType);
    if (type != null) {
      type = type.toUpperCase(Locale.ROOT);
    }
    boolean yearly = "YEARLY".equalsIgnoreCase(cycle) || "ANNUAL".equalsIgnoreCase(cycle);
    List<String> features =
        jdbc.query(
            "SELECT feature_code FROM plan_feature WHERE plan_id = ? AND enabled = TRUE ORDER BY feature_code",
            (rs, row) -> rs.getString(1),
            planId);
    long list = 0;
    List<Map<String, Object>> lines = new ArrayList<>();
    for (String code : features) {
      Long typed = openAmount(code, type, yearly);
      Long standard = openAmount(code, null, yearly);
      long amount = RateCardMath.rateMinor(type == null ? null : typed, standard);
      list += amount;
      Map<String, Object> line = new LinkedHashMap<>();
      line.put("featureCode", code);
      line.put("amountMinor", amount);
      line.put("source", type != null && typed != null ? "BUSINESS_TYPE" : standard == null ? "UNPRICED" : "STANDARD");
      lines.add(line);
    }
    int discountBps = openDiscountBps(planId);
    long discount = RateCardMath.discountMinor(list, discountBps);
    long selling = RateCardMath.sellingMinor(list, discountBps);
    int gstBps = defaultGstBps();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("planId", planId);
    out.put("businessTypeCode", type == null ? "" : type);
    out.put("cycle", yearly ? "YEARLY" : "MONTHLY");
    out.put("lines", lines);
    out.put("listMinor", list);
    out.put("discountBps", discountBps);
    out.put("discountMinor", discount);
    out.put("sellingMinor", selling);
    out.put("gstBps", gstBps);
    out.put("gstOnSellingMinor", RateCardMath.gstMinor(selling, gstBps, false));
    out.put("note", "Calculated from the rate card. The price book and existing subscriptions are unchanged.");
    return out;
  }

  @Transactional
  public Map<String, Object> revisePlanDiscount(String planId, Map<String, Object> body) {
    Integer plans =
        jdbc.queryForObject("SELECT COUNT(*) FROM subscription_plan WHERE id = ?", Integer.class, planId);
    if (plans == null || plans == 0) {
      throw new IllegalArgumentException("Unknown plan: " + planId);
    }
    int bps = (int) nonNegative(body.get("discountBps"), "discountBps");
    if (bps > 10_000) {
      throw new IllegalArgumentException("discountBps cannot exceed 10000");
    }
    String reason = required(body, "reason");
    jdbc.update(
        "UPDATE plan_rate_discount SET effective_until = NOW() WHERE plan_id = ? AND effective_until IS NULL",
        planId);
    Long id =
        jdbc.queryForObject(
            """
            INSERT INTO plan_rate_discount (plan_id, discount_bps, reason, changed_by)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            planId,
            bps,
            reason,
            blankToNull(text(body, "changedBy")));
    return Map.of("id", id, "planId", planId, "discountBps", bps);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listDiscounts() {
    return jdbc.query(
        """
        SELECT id, plan_id, discount_bps, reason, effective_from, effective_until, changed_by
          FROM plan_rate_discount
         ORDER BY effective_from DESC
        """,
        (rs, row) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("id", rs.getLong("id"));
          item.put("planId", rs.getString("plan_id"));
          item.put("discountBps", rs.getInt("discount_bps"));
          item.put("reason", rs.getString("reason"));
          item.put("effectiveFrom", rs.getTimestamp("effective_from"));
          item.put("effectiveUntil", rs.getTimestamp("effective_until"));
          item.put("changedBy", rs.getString("changed_by"));
          return item;
        });
  }

  @Transactional
  public Map<String, Object> createQuote(Map<String, Object> body) {
    String customer = required(body, "customerName");
    long standard = nonNegative(body.get("standardAmountMinor"), "standardAmountMinor");
    int bps = (int) nonNegative(body.get("discountBps"), "discountBps");
    if (bps > 10_000) {
      throw new IllegalArgumentException("discountBps cannot exceed 10000");
    }
    long expected = RateCardMath.sellingMinor(standard, bps);
    long supplied = body.get("finalAmountMinor") == null ? expected : nonNegative(body.get("finalAmountMinor"), "finalAmountMinor");
    if (supplied != expected) {
      throw new IllegalArgumentException("finalAmountMinor must equal the discounted standard amount");
    }
    Long id =
        jdbc.queryForObject(
            """
            INSERT INTO pricing_quote (
              customer_name, organization_id, business_type_code, plan_id, feature_codes,
              standard_amount_minor, discount_bps, final_amount_minor, gst_inclusive, billing_cycle,
              valid_from, valid_until, status, reason, created_by
            ) VALUES (
              ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS DATE), CAST(? AS DATE), 'DRAFT', ?, ?
            )
            RETURNING id
            """,
            Long.class,
            customer,
            blankToNull(text(body, "organizationId")),
            blankToNull(text(body, "businessTypeCode")),
            blankToNull(text(body, "planId")),
            text(body, "featureCodes") == null ? "" : text(body, "featureCodes"),
            standard,
            bps,
            supplied,
            bool(body.get("gstInclusive")),
            yearly(text(body, "billingCycle")) ? "YEARLY" : "MONTHLY",
            blankToNull(text(body, "validFrom")),
            blankToNull(text(body, "validUntil")),
            blankToNull(text(body, "reason")),
            blankToNull(text(body, "createdBy")));
    return Map.of("id", id, "status", "DRAFT", "finalAmountMinor", supplied);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listQuotes() {
    return jdbc.query(
        """
        SELECT id, customer_name, organization_id, business_type_code, plan_id,
               standard_amount_minor, discount_bps, final_amount_minor, billing_cycle,
               valid_from, valid_until, status, reason, created_by, approved_by, created_at
          FROM pricing_quote
         ORDER BY created_at DESC
        """,
        (rs, row) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("id", rs.getLong("id"));
          item.put("customerName", rs.getString("customer_name"));
          item.put("organizationId", rs.getString("organization_id"));
          item.put("businessTypeCode", rs.getString("business_type_code"));
          item.put("planId", rs.getString("plan_id"));
          item.put("standardAmountMinor", rs.getLong("standard_amount_minor"));
          item.put("discountBps", rs.getInt("discount_bps"));
          item.put("finalAmountMinor", rs.getLong("final_amount_minor"));
          item.put("billingCycle", rs.getString("billing_cycle"));
          item.put("validFrom", rs.getDate("valid_from"));
          item.put("validUntil", rs.getDate("valid_until"));
          item.put("status", rs.getString("status"));
          item.put("reason", rs.getString("reason"));
          item.put("createdBy", rs.getString("created_by"));
          item.put("approvedBy", rs.getString("approved_by"));
          item.put("createdAt", rs.getTimestamp("created_at"));
          return item;
        });
  }

  @Transactional
  public Map<String, Object> setQuoteStatus(long id, Map<String, Object> body) {
    String status = required(body, "status").toUpperCase(Locale.ROOT);
    if (!Set.of("DRAFT", "PENDING", "APPROVED", "REJECTED").contains(status)) {
      throw new IllegalArgumentException("Unknown quote status: " + status);
    }
    String approver = blankToNull(text(body, "approvedBy"));
    if ("APPROVED".equals(status) && approver == null) {
      throw new IllegalArgumentException("approvedBy is required");
    }
    int updated =
        jdbc.update(
            """
            UPDATE pricing_quote
               SET status = ?, approved_by = COALESCE(?, approved_by), updated_at = NOW()
             WHERE id = ?
            """,
            status,
            approver,
            id);
    if (updated == 0) {
      throw new IllegalArgumentException("Unknown quote: " + id);
    }
    return Map.of("id", id, "status", status);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> dashboard() {
    Integer features =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM feature_definition WHERE active = TRUE", Integer.class);
    Integer priced =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM feature_rate_version
             WHERE effective_until IS NULL AND business_type_code IS NULL AND monthly_amount_minor > 0
            """,
            Integer.class);
    Integer free =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM feature_definition f
             WHERE f.active = TRUE
               AND NOT EXISTS (
                 SELECT 1 FROM feature_rate_version r
                  WHERE r.feature_code = f.code
                    AND r.effective_until IS NULL
                    AND r.business_type_code IS NULL
                    AND r.monthly_amount_minor > 0
               )
            """,
            Integer.class);
    Integer addons =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM feature_rate_version
             WHERE effective_until IS NULL AND sellable_addon = TRUE
            """,
            Integer.class);
    Long average =
        jdbc.queryForObject(
            """
            SELECT COALESCE(AVG(monthly_amount_minor), 0) FROM feature_rate_version
             WHERE effective_until IS NULL AND business_type_code IS NULL
            """,
            Long.class);
    List<Map<String, Object>> highest =
        jdbc.query(
            """
            SELECT feature_code, monthly_amount_minor
              FROM feature_rate_version
             WHERE effective_until IS NULL AND business_type_code IS NULL
             ORDER BY monthly_amount_minor DESC
             LIMIT 8
            """,
            (rs, row) -> Map.of("featureCode", rs.getString(1), "monthlyAmountMinor", rs.getLong(2)));
    List<Map<String, Object>> used =
        jdbc.query(
            """
            SELECT feature_code, COUNT(*) AS plan_count
              FROM plan_feature
             WHERE enabled = TRUE
             GROUP BY feature_code
             ORDER BY plan_count DESC
             LIMIT 8
            """,
            (rs, row) -> Map.of("featureCode", rs.getString(1), "planCount", rs.getLong(2)));
    Integer plans = jdbc.queryForObject("SELECT COUNT(*) FROM subscription_plan WHERE active = TRUE", Integer.class);
    Integer subs =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM tenant_subscription ts
              LEFT JOIN tenant_subscription_lifecycle lc ON lc.organization_id = ts.organization_id
             WHERE lc.status IS NULL OR lc.status = 'ACTIVE'
            """,
            Integer.class);
    List<Map<String, Object>> byPlan =
        jdbc.query(
            """
            SELECT ts.plan_id, COUNT(*) AS subscriptions,
                   COALESCE(MAX(pp.amount_minor), 0) AS price_book_monthly_minor
              FROM tenant_subscription ts
              LEFT JOIN tenant_subscription_lifecycle lc ON lc.organization_id = ts.organization_id
              LEFT JOIN plan_price pp
                ON pp.plan_id = ts.plan_id
               AND pp.active = TRUE
               AND pp.billing_cycle_code = 'MONTHLY'
               AND pp.price_book_id = (SELECT id FROM price_book WHERE is_default = TRUE ORDER BY id LIMIT 1)
             WHERE lc.status IS NULL OR lc.status = 'ACTIVE'
             GROUP BY ts.plan_id
             ORDER BY subscriptions DESC
            """,
            (rs, row) -> {
              Map<String, Object> item = new LinkedHashMap<>();
              item.put("planId", rs.getString("plan_id"));
              item.put("subscriptions", rs.getLong("subscriptions"));
              item.put("priceBookMonthlyMinor", rs.getLong("price_book_monthly_minor"));
              return item;
            });
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureCount", features);
    out.put("paidFeatureCount", priced);
    out.put("freeFeatureCount", free);
    out.put("addonCount", addons);
    out.put("averageMonthlyMinor", average);
    out.put("highestValue", highest);
    out.put("mostUsed", used);
    out.put("planCount", plans);
    out.put("activeSubscriptionCount", subs);
    out.put("revenueByPlan", byPlan);
    out.put(
        "revenueNote",
        "Revenue by plan uses the current default price book, not the rate card. Subscriptions do not store business type, so revenue by business type is not invented here.");
    return out;
  }

  private Long openAmount(String featureCode, String businessType, boolean yearly) {
    String column = yearly ? "yearly_amount_minor" : "monthly_amount_minor";
    List<Long> rows =
        jdbc.query(
            "SELECT "
                + column
                + " FROM feature_rate_version WHERE feature_code = ? AND effective_until IS NULL AND business_type_code IS NOT DISTINCT FROM ?",
            (rs, row) -> rs.getLong(1),
            featureCode,
            businessType);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private int openDiscountBps(String planId) {
    List<Integer> rows =
        jdbc.query(
            "SELECT discount_bps FROM plan_rate_discount WHERE plan_id = ? AND effective_until IS NULL",
            (rs, row) -> rs.getInt(1),
            planId);
    return rows.isEmpty() ? 0 : rows.get(0);
  }

  private int defaultGstBps() {
    List<Integer> rows =
        jdbc.query(
            """
            SELECT COALESCE(cgst_bps, 0) + COALESCE(sgst_bps, 0)
              FROM tax_rule
             WHERE active = TRUE AND is_default = TRUE
             ORDER BY code
             LIMIT 1
            """,
            (rs, row) -> rs.getInt(1));
    if (rows.isEmpty() || rows.get(0) == 0) {
      return RateCardMath.GST_18_BPS;
    }
    return rows.get(0);
  }

  private Map<String, Object> rate(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", rs.getLong("id"));
    item.put("featureCode", rs.getString("feature_code"));
    item.put("featureName", rs.getString("feature_name"));
    item.put("moduleCode", rs.getString("module_code"));
    item.put("businessTypeCode", rs.getString("business_type_code"));
    item.put("unitModel", rs.getString("unit_model"));
    item.put("monthlyAmountMinor", rs.getLong("monthly_amount_minor"));
    item.put("yearlyAmountMinor", rs.getLong("yearly_amount_minor"));
    item.put("gstInclusive", rs.getBoolean("gst_inclusive"));
    item.put("sellableAddon", rs.getBoolean("sellable_addon"));
    item.put("effectiveFrom", rs.getTimestamp("effective_from"));
    item.put("effectiveUntil", rs.getTimestamp("effective_until"));
    item.put("changedBy", rs.getString("changed_by"));
    item.put("changeReason", rs.getString("change_reason"));
    item.put("benchmarkLowMinor", rs.getObject("benchmark_low_minor"));
    item.put("benchmarkAverageMinor", rs.getObject("benchmark_average_minor"));
    item.put("benchmarkHighMinor", rs.getObject("benchmark_high_minor"));
    item.put("benchmarkNotes", rs.getString("benchmark_notes"));
    item.put("benchmarkReviewedOn", rs.getDate("benchmark_reviewed_on"));
    return item;
  }

  private static boolean yearly(String cycle) {
    return "YEARLY".equalsIgnoreCase(cycle) || "ANNUAL".equalsIgnoreCase(cycle);
  }

  private static String text(Map<String, Object> body, String key) {
    Object value = body.get(key);
    return value == null ? null : String.valueOf(value).trim();
  }

  private static String required(Map<String, Object> body, String key) {
    String value = text(body, key);
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(key + " is required");
    }
    return value;
  }

  private static String blankToNull(String value) {
    return StringUtils.hasText(value) ? value : null;
  }

  private static boolean bool(Object value) {
    if (value instanceof Boolean flag) {
      return flag;
    }
    return value != null && "true".equalsIgnoreCase(String.valueOf(value));
  }

  private static long nonNegative(Object value, String key) {
    if (value == null || !StringUtils.hasText(String.valueOf(value))) {
      return 0;
    }
    long parsed;
    try {
      parsed = Long.parseLong(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + " must be a whole number of paise");
    }
    if (parsed < 0) {
      throw new IllegalArgumentException(key + " cannot be negative");
    }
    return parsed;
  }

  private static Long optionalLong(Object value) {
    if (value == null || !StringUtils.hasText(String.valueOf(value))) {
      return null;
    }
    return nonNegative(value, "benchmark");
  }
}
