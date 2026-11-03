package com.sugamflow.school.subscription.billing;

import java.util.Optional;

public interface SchoolPackageRateSource {
  Optional<SchoolPackageRate> openSchoolRate();
}
