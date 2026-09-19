package com.meridiantrust.sentinel.common.security;

/**
 * Role constants. String literals scattered through {@code @PreAuthorize}
 * annotations are a silent-failure hazard — a typo grants access rather than
 * denying it, because the expression simply never matches nothing. Centralising
 * them makes a typo a compile error.
 */
public final class Roles {

    private Roles() {}

    public static final String ANALYST = "ANALYST";
    public static final String SENIOR_ANALYST = "SENIOR_ANALYST";
    public static final String COMPLIANCE_ADMIN = "COMPLIANCE_ADMIN";

    /** SpEL fragments for use in {@code @PreAuthorize}. */
    public static final String HAS_ANALYST = "hasRole('" + ANALYST + "')";
    public static final String HAS_SENIOR = "hasRole('" + SENIOR_ANALYST + "')";
    public static final String HAS_ADMIN = "hasRole('" + COMPLIANCE_ADMIN + "')";
}
