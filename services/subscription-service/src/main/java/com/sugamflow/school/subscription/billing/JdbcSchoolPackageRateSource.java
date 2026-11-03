package com.sugamflow.school.subscription.billing;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcSchoolPackageRateSource implements SchoolPackageRateSource {

  private final JdbcTemplate jdbc;

  public JdbcSchoolPackageRateSource(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<SchoolPackageRate> openSchoolRate() {
    List<SchoolPackageRate> rows =
        jdbc.query(
            """
            SELECT id, monthly_amount_minor, gst_inclusive
              FROM package_rate_version
             WHERE business_type_code = 'SCHOOL'
               AND unit_model = 'PER_STUDENT'
               AND effective_until IS NULL
            """,
            (rs, row) ->
                new SchoolPackageRate(
                    rs.getLong("id"), rs.getLong("monthly_amount_minor"), rs.getBoolean("gst_inclusive")));
    return rows.stream().findFirst();
  }
}
