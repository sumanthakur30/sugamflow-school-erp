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

  static final Set<String> PACKAGE_UNITS;

  static {
    PACKAGE_UNITS = new java.util.HashSet<>(UNIT_MODELS);
    PACKAGE_UNITS.add("PER_STUDENT");
  }

  private static final String RATE_SELECT =
      """
      SELECT r.id, r.feature_code, f.name AS feature_name, f.module_code,
             r.business_type_code, r.unit_model, r.monthly_amount_minor, r.yearly_amount_minor,
             r.gst_inclusive, r.sellable_addon, r.effective_from, r.effective_until,
             r.changed_by, r.change_reason, r.benchmark_low_minor, r.benchmark_average_minor,
             r.benchmark_high_minor, r.benchmark_notes, r.benchmark_reviewed_on,
             r.recommended_discount_bps, r.max_discount_bps, r.benchmark_source
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
              benchmark_notes, benchmark_reviewed_on,
              recommended_discount_bps, max_discount_bps, benchmark_source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS DATE), ?, ?, ?)
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
            blankToNull(text(body, "benchmarkReviewedOn")),
            optionalInt(body.get("recommendedDiscountBps")),
            optionalInt(body.get("maxDiscountBps")),
            blankToNull(text(body, "benchmarkSource")));
    return Map.of("id", id, "featureCode", featureCode, "businessTypeCode", businessType == null ? "" : businessType);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listPackages(boolean openOnly) {
    String sql =
        """
        SELECT p.id, p.business_type_code, b.name AS business_type_name, p.package_name, p.unit_model,
               p.monthly_amount_minor, p.yearly_amount_minor, p.min_selling_minor, p.gst_inclusive,
               p.recommended_discount_bps, p.max_discount_bps, p.included_summary,
               p.effective_from, p.effective_until, p.changed_by, p.change_reason,
               p.benchmark_low_minor, p.benchmark_average_minor, p.benchmark_high_minor,
               p.benchmark_source, p.benchmark_notes, p.benchmark_reviewed_on
          FROM package_rate_version p
          JOIN business_type b ON b.code = p.business_type_code
        """;
    if (openOnly) {
      sql += " WHERE p.effective_until IS NULL";
    }
    sql += " ORDER BY b.sort_order, p.effective_from DESC";
    return jdbc.query(sql, (rs, row) -> packageRow(rs));
  }

  @Transactional
  public Map<String, Object> revisePackage(Map<String, Object> body) {
    String type = required(body, "businessTypeCode").toUpperCase(Locale.ROOT);
    Integer known =
        jdbc.queryForObject("SELECT COUNT(*) FROM business_type WHERE code = ?", Integer.class, type);
    if (known == null || known == 0) {
      throw new IllegalArgumentException("Unknown business type: " + type);
    }
    String packageName = required(body, "packageName");
    String unit = text(body, "unitModel");
    if (!StringUtils.hasText(unit)) {
      unit = "PER_ORGANIZATION";
    }
    unit = unit.trim().toUpperCase(Locale.ROOT);
    if (!PACKAGE_UNITS.contains(unit)) {
      throw new IllegalArgumentException("Unknown unit model: " + unit);
    }
    int recommended = optionalInt(body.get("recommendedDiscountBps")) == null
        ? 1000
        : optionalInt(body.get("recommendedDiscountBps"));
    int maxDiscount = optionalInt(body.get("maxDiscountBps")) == null
        ? 2000
        : optionalInt(body.get("maxDiscountBps"));
    if (recommended > maxDiscount) {
      throw new IllegalArgumentException("Recommended discount cannot exceed the maximum discount");
    }
    String reason = required(body, "changeReason");
    jdbc.update(
        "UPDATE package_rate_version SET effective_until = NOW() WHERE business_type_code = ? AND effective_until IS NULL",
        type);
    Long id =
        jdbc.queryForObject(
            """
            INSERT INTO package_rate_version (
              business_type_code, package_name, unit_model, monthly_amount_minor, yearly_amount_minor,
              min_selling_minor, gst_inclusive, recommended_discount_bps, max_discount_bps, included_summary,
              changed_by, change_reason, benchmark_low_minor, benchmark_average_minor, benchmark_high_minor,
              benchmark_source, benchmark_notes, benchmark_reviewed_on
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS DATE))
            RETURNING id
            """,
            Long.class,
            type,
            packageName,
            unit,
            nonNegative(body.get("monthlyAmountMinor"), "monthlyAmountMinor"),
            nonNegative(body.get("yearlyAmountMinor"), "yearlyAmountMinor"),
            nonNegative(body.get("minSellingMinor"), "minSellingMinor"),
            bool(body.get("gstInclusive")),
            recommended,
            maxDiscount,
            blankToNull(text(body, "includedSummary")),
            blankToNull(text(body, "changedBy")),
            reason,
            optionalLong(body.get("benchmarkLowMinor")),
            optionalLong(body.get("benchmarkAverageMinor")),
            optionalLong(body.get("benchmarkHighMinor")),
            blankToNull(text(body, "benchmarkSource")),
            blankToNull(text(body, "benchmarkNotes")),
            blankToNull(text(body, "benchmarkReviewedOn")));
    return Map.of("id", id, "businessTypeCode", type);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> previewProposal(Map<String, Object> body) {
    return quoteMath(body);
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
    Map<String, Object> math = quoteMath(body);
    long standard = ((Number) math.get("standardAmountMinor")).longValue();
    int bps = ((Number) math.get("discountBps")).intValue();
    long supplied = ((Number) math.get("finalAmountMinor")).longValue();
    boolean approval = Boolean.TRUE.equals(math.get("approvalRequired"));
    String status = approval ? "PENDING" : "DRAFT";
    Long id =
        jdbc.queryForObject(
            """
            INSERT INTO pricing_quote (
              customer_name, organization_id, business_type_code, plan_id, feature_codes,
              standard_amount_minor, discount_bps, final_amount_minor, gst_inclusive, billing_cycle,
              valid_from, valid_until, status, reason, created_by,
              input_mode, recommended_discount_bps, max_discount_bps, approval_required,
              discount_amount_minor, gst_amount_minor, rounding_step
            ) VALUES (
              ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS DATE), CAST(? AS DATE), ?, ?, ?,
              ?, ?, ?, ?, ?, ?, ?
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
            Boolean.TRUE.equals(math.get("gstInclusive")),
            math.get("billingCycle"),
            blankToNull(text(body, "validFrom")),
            blankToNull(text(body, "validUntil")),
            status,
            blankToNull(text(body, "reason")),
            blankToNull(text(body, "createdBy")),
            math.get("inputMode"),
            math.get("recommendedDiscountBps"),
            math.get("maxDiscountBps"),
            approval,
            math.get("discountAmountMinor"),
            math.get("gstAmountMinor"),
            math.get("roundingStep"));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", id);
    out.put("status", status);
    out.put("approvalRequired", approval);
    out.put("finalAmountMinor", supplied);
    out.put("discountBps", bps);
    out.put("standardAmountMinor", standard);
    out.put("roundingStep", math.get("roundingStep"));
    return out;
  }

  @Transactional
  public Map<String, Object> updateQuote(long id, Map<String, Object> body) {
    Integer existing =
        jdbc.queryForObject("SELECT COUNT(*) FROM pricing_quote WHERE id = ?", Integer.class, id);
    if (existing == null || existing == 0) {
      throw new IllegalArgumentException("Unknown quote: " + id);
    }
    String customer = required(body, "customerName");
    Map<String, Object> math = quoteMath(body);
    long standard = ((Number) math.get("standardAmountMinor")).longValue();
    int bps = ((Number) math.get("discountBps")).intValue();
    long supplied = ((Number) math.get("finalAmountMinor")).longValue();
    boolean approval = Boolean.TRUE.equals(math.get("approvalRequired"));
    String status = approval ? "PENDING" : "DRAFT";
    String featureCodes = text(body, "featureCodes");
    int updated =
        jdbc.update(
            """
            UPDATE pricing_quote
               SET customer_name = ?,
                   organization_id = ?,
                   business_type_code = ?,
                   plan_id = ?,
                   feature_codes = COALESCE(?, feature_codes),
                   standard_amount_minor = ?,
                   discount_bps = ?,
                   final_amount_minor = ?,
                   gst_inclusive = ?,
                   billing_cycle = ?,
                   valid_from = CAST(? AS DATE),
                   valid_until = CAST(? AS DATE),
                   status = ?,
                   reason = ?,
                   approved_by = NULL,
                   input_mode = ?,
                   recommended_discount_bps = ?,
                   max_discount_bps = ?,
                   approval_required = ?,
                   discount_amount_minor = ?,
                   gst_amount_minor = ?,
                   rounding_step = ?,
                   updated_at = NOW()
             WHERE id = ?
            """,
            customer,
            blankToNull(text(body, "organizationId")),
            blankToNull(text(body, "businessTypeCode")),
            blankToNull(text(body, "planId")),
            featureCodes == null ? null : featureCodes,
            standard,
            bps,
            supplied,
            Boolean.TRUE.equals(math.get("gstInclusive")),
            math.get("billingCycle"),
            blankToNull(text(body, "validFrom")),
            blankToNull(text(body, "validUntil")),
            status,
            blankToNull(text(body, "reason")),
            math.get("inputMode"),
            math.get("recommendedDiscountBps"),
            math.get("maxDiscountBps"),
            approval,
            math.get("discountAmountMinor"),
            math.get("gstAmountMinor"),
            math.get("roundingStep"),
            id);
    if (updated == 0) {
      throw new IllegalArgumentException("Unknown quote: " + id);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", id);
    out.put("status", status);
    out.put("approvalRequired", approval);
    out.put("finalAmountMinor", supplied);
    out.put("discountBps", bps);
    out.put("standardAmountMinor", standard);
    out.put("roundingStep", math.get("roundingStep"));
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listQuotes() {
    return jdbc.query(
        """
        SELECT id, customer_name, organization_id, business_type_code, plan_id, feature_codes,
               standard_amount_minor, discount_bps, final_amount_minor, billing_cycle,
               valid_from, valid_until, status, reason, created_by, approved_by, created_at,
               input_mode, recommended_discount_bps, max_discount_bps, approval_required,
               discount_amount_minor, gst_amount_minor, gst_inclusive, rounding_step
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
          item.put("featureCodes", rs.getString("feature_codes"));
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
          item.put("inputMode", rs.getString("input_mode"));
          item.put("recommendedDiscountBps", rs.getObject("recommended_discount_bps"));
          item.put("maxDiscountBps", rs.getObject("max_discount_bps"));
          item.put("approvalRequired", rs.getBoolean("approval_required"));
          item.put("discountAmountMinor", rs.getObject("discount_amount_minor"));
          item.put("gstAmountMinor", rs.getObject("gst_amount_minor"));
          item.put("gstInclusive", rs.getBoolean("gst_inclusive"));
          item.put("roundingStep", rs.getString("rounding_step"));
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
    Integer businessTypes =
        jdbc.queryForObject("SELECT COUNT(*) FROM business_type WHERE active = TRUE", Integer.class);
    Integer priceBooks =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM package_rate_version WHERE effective_until IS NULL", Integer.class);
    Integer pending =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pricing_quote WHERE status = 'PENDING'", Integer.class);
    Integer quotes = jdbc.queryForObject("SELECT COUNT(*) FROM pricing_quote", Integer.class);
    Integer changes =
        jdbc.queryForObject(
            """
            SELECT
              (SELECT COUNT(*) FROM package_rate_version WHERE created_at >= date_trunc('month', NOW()))
              + (SELECT COUNT(*) FROM feature_rate_version WHERE created_at >= date_trunc('month', NOW()))
            """,
            Integer.class);
    Double offered =
        jdbc.queryForObject(
            "SELECT AVG(discount_bps) FROM pricing_quote", Double.class);
    out.put("businessTypeCount", businessTypes);
    out.put("activePriceBookCount", priceBooks);
    out.put("pendingApprovalCount", pending);
    out.put("quoteCount", quotes);
    out.put("priceChangesThisMonth", changes);
    out.put("averageDiscountBps", offered == null ? 0 : Math.round(offered));
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
    item.put("recommendedDiscountBps", rs.getObject("recommended_discount_bps"));
    item.put("maxDiscountBps", rs.getObject("max_discount_bps"));
    item.put("benchmarkSource", rs.getString("benchmark_source"));
    return item;
  }

  private Map<String, Object> quoteMath(Map<String, Object> body) {
    String type = blankToNull(text(body, "businessTypeCode"));
    if (type != null) {
      type = type.toUpperCase(Locale.ROOT);
    }
    boolean yearlyCycle = yearly(text(body, "billingCycle"));
    String inputMode = text(body, "inputMode");
    if (!StringUtils.hasText(inputMode)) {
      inputMode = body.get("proposalAmountMinor") != null ? "AMOUNT" : "DISCOUNT";
    }
    inputMode = inputMode.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("DISCOUNT", "AMOUNT").contains(inputMode)) {
      throw new IllegalArgumentException("inputMode must be DISCOUNT or AMOUNT");
    }
    long standard;
    int recommended = 0;
    int maxDiscount = 10_000;
    boolean gstInclusive = bool(body.get("gstInclusive"));
    String packageName = "";
    String unit = "";
    long minSelling = 0;
    long monthly = 0;
    long yearlyAmount = 0;
    if (type != null) {
      Map<String, Object> pkg = openPackage(type);
      standard = yearlyCycle
          ? ((Number) pkg.get("yearlyAmountMinor")).longValue()
          : ((Number) pkg.get("monthlyAmountMinor")).longValue();
      recommended = ((Number) pkg.get("recommendedDiscountBps")).intValue();
      maxDiscount = ((Number) pkg.get("maxDiscountBps")).intValue();
      gstInclusive = Boolean.TRUE.equals(pkg.get("gstInclusive"));
      packageName = String.valueOf(pkg.get("packageName"));
      unit = String.valueOf(pkg.get("unitModel"));
      minSelling = ((Number) pkg.get("minSellingMinor")).longValue();
      monthly = ((Number) pkg.get("monthlyAmountMinor")).longValue();
      yearlyAmount = ((Number) pkg.get("yearlyAmountMinor")).longValue();
    } else {
      standard = nonNegative(body.get("standardAmountMinor"), "standardAmountMinor");
    }
    int bps;
    long finalAmount;
    if ("AMOUNT".equals(inputMode)) {
      finalAmount = nonNegative(body.get("proposalAmountMinor"), "proposalAmountMinor");
      if (finalAmount > standard) {
        throw new IllegalArgumentException("Proposal amount cannot exceed the standard price");
      }
      bps = RateCardMath.bpsFromPrices(standard, finalAmount);
    } else {
      bps = (int) nonNegative(body.get("discountBps"), "discountBps");
      if (bps > 10_000) {
        throw new IllegalArgumentException("discountBps cannot exceed 10000");
      }
      finalAmount = RateCardMath.sellingMinor(standard, bps);
      if (body.get("finalAmountMinor") != null && "NONE".equals(roundingStep(text(body, "roundingStep")))) {
        long supplied = nonNegative(body.get("finalAmountMinor"), "finalAmountMinor");
        if (supplied != finalAmount) {
          throw new IllegalArgumentException("finalAmountMinor must equal the discounted standard amount");
        }
      }
    }
    String step = roundingStep(text(body, "roundingStep"));
    long exactFinal = finalAmount;
    finalAmount = RateCardMath.roundMinor(finalAmount, step);
    if (finalAmount > standard) {
      finalAmount = standard;
    }
    int effectiveBps = RateCardMath.bpsFromPrices(standard, finalAmount);
    boolean approval = RateCardMath.approvalRequired(Math.max(bps, effectiveBps), maxDiscount);
    int gstBps = defaultGstBps();
    long gst = RateCardMath.gstMinor(finalAmount, gstBps, gstInclusive);
    long payable = gstInclusive ? finalAmount : finalAmount + gst;
    long listYear = monthly * 12;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("businessTypeCode", type == null ? "" : type);
    out.put("packageName", packageName);
    out.put("unitModel", unit);
    out.put("billingCycle", yearlyCycle ? "YEARLY" : "MONTHLY");
    out.put("inputMode", inputMode);
    out.put("standardAmountMinor", standard);
    out.put("monthlyAmountMinor", monthly);
    out.put("yearlyAmountMinor", yearlyAmount);
    out.put("minSellingMinor", minSelling);
    out.put("recommendedDiscountBps", type == null ? null : recommended);
    out.put("maxDiscountBps", type == null ? null : maxDiscount);
    out.put("discountBps", bps);
    out.put("discountAmountMinor", Math.max(0, standard - finalAmount));
    out.put("exactFinalAmountMinor", exactFinal);
    out.put("finalAmountMinor", finalAmount);
    out.put("roundingStep", step);
    out.put("gstInclusive", gstInclusive);
    out.put("gstBps", gstBps);
    out.put("gstAmountMinor", gst);
    out.put("payableAmountMinor", payable);
    out.put("approvalRequired", approval);
    out.put("annualListMinor", listYear);
    out.put("annualSavingMinor", Math.max(0, listYear - yearlyAmount));
    out.put("effectiveMonthlyMinor", yearlyCycle ? finalAmount / 12 : finalAmount);
    out.put(
        "note",
        approval
            ? "Discount exceeds the permitted limit. Super Admin approval is required."
            : "Within the approval limit. The standard price book is unchanged.");
    return out;
  }

  private Map<String, Object> openPackage(String businessType) {
    List<Map<String, Object>> rows =
        jdbc.query(
            """
            SELECT id, business_type_code, package_name, unit_model, monthly_amount_minor, yearly_amount_minor,
                   min_selling_minor, gst_inclusive, recommended_discount_bps, max_discount_bps
              FROM package_rate_version
             WHERE business_type_code = ? AND effective_until IS NULL
            """,
            (rs, row) -> {
              Map<String, Object> item = new LinkedHashMap<>();
              item.put("id", rs.getLong("id"));
              item.put("packageName", rs.getString("package_name"));
              item.put("unitModel", rs.getString("unit_model"));
              item.put("monthlyAmountMinor", rs.getLong("monthly_amount_minor"));
              item.put("yearlyAmountMinor", rs.getLong("yearly_amount_minor"));
              item.put("minSellingMinor", rs.getLong("min_selling_minor"));
              item.put("gstInclusive", rs.getBoolean("gst_inclusive"));
              item.put("recommendedDiscountBps", rs.getInt("recommended_discount_bps"));
              item.put("maxDiscountBps", rs.getInt("max_discount_bps"));
              return item;
            },
            businessType);
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("No standard price for business type: " + businessType);
    }
    return rows.get(0);
  }

  private Map<String, Object> packageRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", rs.getLong("id"));
    item.put("businessTypeCode", rs.getString("business_type_code"));
    item.put("businessTypeName", rs.getString("business_type_name"));
    item.put("packageName", rs.getString("package_name"));
    item.put("unitModel", rs.getString("unit_model"));
    item.put("monthlyAmountMinor", rs.getLong("monthly_amount_minor"));
    item.put("yearlyAmountMinor", rs.getLong("yearly_amount_minor"));
    item.put("minSellingMinor", rs.getLong("min_selling_minor"));
    item.put("gstInclusive", rs.getBoolean("gst_inclusive"));
    item.put("recommendedDiscountBps", rs.getInt("recommended_discount_bps"));
    item.put("maxDiscountBps", rs.getInt("max_discount_bps"));
    item.put("includedSummary", rs.getString("included_summary"));
    item.put("effectiveFrom", rs.getTimestamp("effective_from"));
    item.put("effectiveUntil", rs.getTimestamp("effective_until"));
    item.put("changedBy", rs.getString("changed_by"));
    item.put("changeReason", rs.getString("change_reason"));
    item.put("benchmarkLowMinor", rs.getObject("benchmark_low_minor"));
    item.put("benchmarkAverageMinor", rs.getObject("benchmark_average_minor"));
    item.put("benchmarkHighMinor", rs.getObject("benchmark_high_minor"));
    item.put("benchmarkSource", rs.getString("benchmark_source"));
    item.put("benchmarkNotes", rs.getString("benchmark_notes"));
    item.put("benchmarkReviewedOn", rs.getDate("benchmark_reviewed_on"));
    long monthly = rs.getLong("monthly_amount_minor");
    long yearly = rs.getLong("yearly_amount_minor");
    long listYear = monthly * 12;
    item.put("annualSavingMinor", Math.max(0, listYear - yearly));
    item.put("annualSavingBps", listYear == 0 ? 0 : Math.max(0, Math.round((listYear - yearly) * 10_000.0 / listYear)));
    return item;
  }

  private static boolean yearly(String cycle) {
    return "YEARLY".equalsIgnoreCase(cycle) || "ANNUAL".equalsIgnoreCase(cycle);
  }

  private static String roundingStep(String raw) {
    if (!StringUtils.hasText(raw)) {
      return "NONE";
    }
    String step = raw.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("NONE", "RUPEE", "TEN", "HUNDRED").contains(step)) {
      throw new IllegalArgumentException("roundingStep must be NONE, RUPEE, TEN, or HUNDRED");
    }
    return step;
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

  private static Integer optionalInt(Object value) {
    if (value == null || !StringUtils.hasText(String.valueOf(value))) {
      return null;
    }
    int parsed;
    try {
      parsed = Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Discount must be a whole number of basis points");
    }
    if (parsed < 0 || parsed > 10_000) {
      throw new IllegalArgumentException("Discount must be between 0 and 10000 basis points");
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
