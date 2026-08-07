package com.sugamflow.school.compliance.dto;

import java.util.List;

public record ImportBootstrapResponse(
    String boardCode,
    String packKey,
    String matchKeyStudent,
    String matchKeyStaff,
    boolean fillBlankOnlyDefault,
    List<ImportColumn> studentColumns,
    List<ImportColumn> staffColumns) {

  public record ImportColumn(
      String fieldKey, String label, boolean required, String severity, String formatRegex) {}
}
