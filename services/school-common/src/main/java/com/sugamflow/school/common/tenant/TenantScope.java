package com.sugamflow.school.common.tenant;

public record TenantScope(
    String organizationId,
    String branchId,
    String academicSessionId,
    String userId,
    String roleCode
) {}
