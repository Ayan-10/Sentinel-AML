package com.meridiantrust.sentinel.common.audit.model;

/** Canonical audit action vocabulary. */
public final class AuditAction {

    private AuditAction() {}

    public static final String ALERT_CREATED = "ALERT_CREATED";
    public static final String ALERT_MERGED = "ALERT_MERGED";
    public static final String ALERT_STATUS_CHANGED = "ALERT_STATUS_CHANGED";
    public static final String ALERT_DISPOSED = "ALERT_DISPOSED";
    public static final String ALERT_LINKED_TO_CASE = "ALERT_LINKED_TO_CASE";

    public static final String CASE_OPENED = "CASE_OPENED";
    public static final String CASE_ASSIGNED = "CASE_ASSIGNED";
    public static final String CASE_STATUS_CHANGED = "CASE_STATUS_CHANGED";
    public static final String CASE_DISPOSED = "CASE_DISPOSED";
    public static final String SAR_DRAFT_GENERATED = "SAR_DRAFT_GENERATED";

    public static final String RULE_UPDATED = "RULE_UPDATED";
    public static final String REFERENCE_DATA_UPDATED = "REFERENCE_DATA_UPDATED";
    public static final String INGESTION_COMPLETED = "INGESTION_COMPLETED";

    public static final String ENTITY_ALERT = "ALERT";
    public static final String ENTITY_CASE = "CASE";
    public static final String ENTITY_RULE = "RULE";
    public static final String ENTITY_REFERENCE = "REFERENCE";
    public static final String ENTITY_INGESTION = "INGESTION";
}
