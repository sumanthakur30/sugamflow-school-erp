package com.sugamflow.school.common.config;

/**
 * Hierarchical configuration scope.
 * Resolution: PLATFORM → PLAN → ORG → BRANCH → SESSION.
 */
public enum ConfigScope {
  PLATFORM,
  PLAN,
  ORGANIZATION,
  BRANCH,
  ACADEMIC_SESSION
}
