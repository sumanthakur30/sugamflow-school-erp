package com.sugamflow.school.compliance.dto;

public record DocumentFolderResponse(
    Long id,
    Long parentId,
    String name,
    String description,
    String visibility,
    String complianceArea,
    int depth) {}
