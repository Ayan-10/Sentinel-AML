package com.meridiantrust.sentinel.alerting.model;

/**
 * The analyst's conclusion. Business rule 6 requires this to be recorded
 * alongside a reason and the analyst's identity whenever an alert is cleared.
 */
public enum Disposition {

    /** Suspicious activity confirmed; remains on file. */
    TRUE_POSITIVE(false),

    /** Benign, explained by legitimate activity. */
    FALSE_POSITIVE(false),

    /** Escalated for Suspicious Activity Report filing. Senior analysts only. */
    ESCALATED_TO_SAR(true),

    /** Reviewed, nothing actionable, no SAR warranted. */
    CLEARED_NO_ACTION(false);

    private final boolean requiresSeniorApproval;

    Disposition(boolean requiresSeniorApproval) {
        this.requiresSeniorApproval = requiresSeniorApproval;
    }

    /**
     * SAR escalation has regulatory consequences and a filing deadline, so the
     * authorisation requirement is a property of the disposition itself rather
     * than a check some endpoint might forget.
     */
    public boolean requiresSeniorApproval() {
        return requiresSeniorApproval;
    }
}
