package com.meridiantrust.sentinel.sar.service;

import com.meridiantrust.sentinel.common.model.Formatting;
import com.meridiantrust.sentinel.sar.model.AlertEvidence;
import com.meridiantrust.sentinel.sar.model.SarDraft;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns structured case evidence into SAR narrative prose.
 *
 * <p>Separated from {@link SarDraftService} on Single Responsibility grounds:
 * that class gathers data, this one writes English. The split means the wording
 * — the part a compliance team will inevitably want to revise — can be changed
 * and tested without touching any query, transaction boundary or security
 * annotation.
 *
 * <p>The method is pure: same inputs, same output, no I/O. That is what lets
 * the narrative be asserted in a plain unit test.
 *
 * <p>The prose follows the structure an FIU expects: who the subject is, what
 * was observed, over what period, why it is suspicious, and what the
 * institution proposes to do. Each rule's own explanation is quoted rather than
 * paraphrased, so the narrative stays traceable to the detection that produced
 * it — a regulator reading the SAR can line every sentence up against an alert.
 */
@Component
public class SarNarrativeComposer {

    public String compose(SarDraft.Subject subject,
                          SarDraft.ActivitySummary activity,
                          List<SarDraft.AccountSummary> accounts,
                          List<AlertEvidence> alerts) {

        StringBuilder narrative = new StringBuilder();

        narrative.append(openingParagraph(subject, activity, accounts));
        narrative.append("\n\n").append(subjectParagraph(subject));
        narrative.append("\n\n").append(activityParagraph(activity, alerts));

        for (AlertEvidence alert : alerts) {
            narrative.append("\n\n").append(findingParagraph(alert));
        }

        narrative.append("\n\n").append(closingParagraph(activity, alerts));
        return narrative.toString();
    }

    private String openingParagraph(SarDraft.Subject subject,
                                    SarDraft.ActivitySummary activity,
                                    List<SarDraft.AccountSummary> accounts) {
        String accountList = accounts.isEmpty()
                ? "accounts held at this institution"
                : accounts.stream().map(SarDraft.AccountSummary::accountId)
                          .collect(Collectors.joining(", "));

        return ("MeridianTrust Bank is reporting suspicious activity identified on %s held by %s "
                + "(customer reference %s). The activity was detected by the bank's automated "
                + "transaction monitoring system between %s and %s and comprises %d transaction(s) "
                + "with an aggregate value of %s.")
                .formatted(accountList,
                        subject.fullName(),
                        subject.customerId(),
                        Formatting.date(activity.periodStart()),
                        Formatting.date(activity.periodEnd()),
                        activity.transactionCount(),
                        Formatting.money(activity.totalValueBase()));
    }

    private String subjectParagraph(SarDraft.Subject subject) {
        StringBuilder sb = new StringBuilder();
        sb.append("SUBJECT: %s, national identifier %s".formatted(
                subject.fullName(),
                subject.nationalId() == null ? "not recorded" : subject.nationalId()));

        if (subject.dateOfBirth() != null) {
            sb.append(", date of birth %s".formatted(subject.dateOfBirth()));
        }
        if (subject.address() != null && !subject.address().isBlank()) {
            sb.append(", resident at %s".formatted(subject.address()));
        }
        if (subject.occupation() != null && !subject.occupation().isBlank()) {
            sb.append(". Stated occupation: %s".formatted(subject.occupation()));
        }
        // NOTE: the parentheses matter. `.formatted()` binds to the last string
        // literal in a concatenation, not to the whole expression, so an
        // unparenthesised chain leaves earlier placeholders unsubstituted.
        sb.append((". The customer has been with the institution since %s and is classified %s risk "
                + "with KYC status %s.").formatted(
                        subject.customerSince() == null ? "an unrecorded date" : subject.customerSince(),
                        subject.riskRating().toLowerCase(),
                        subject.kycStatus()));

        // A PEP flag materially changes the handling of a report, so it is
        // called out explicitly rather than left for a reader to notice in a
        // field somewhere.
        if (subject.politicallyExposed()) {
            sb.append(" The subject is a POLITICALLY EXPOSED PERSON, which requires enhanced "
                    + "scrutiny of the activity described below.");
        }
        return sb.toString();
    }

    private String activityParagraph(SarDraft.ActivitySummary activity, List<AlertEvidence> alerts) {
        String typologies = alerts.stream()
                .map(AlertEvidence::typology)
                .distinct()
                .map(t -> t.replace('_', ' ').toLowerCase())
                .collect(Collectors.joining(", "));

        return ("SUSPICIOUS ACTIVITY: the monitoring system raised %d alert(s) against this customer, "
                + "the highest scoring %d out of 100. The activity is consistent with the following "
                + "typolog%s: %s. Each finding is set out below, with the supporting transactions "
                + "listed in the accompanying schedule.")
                .formatted(activity.alertCount(),
                        activity.highestRiskScore(),
                        alerts.stream().map(AlertEvidence::typology).distinct().count() == 1 ? "y" : "ies",
                        typologies);
    }

    private String findingParagraph(AlertEvidence alert) {
        return ("FINDING %s (%s, risk score %d, severity %s): %s")
                .formatted(alert.alertRef(),
                        alert.ruleCode().replace('_', ' '),
                        alert.riskScore(),
                        alert.severity(),
                        alert.explanation());
    }

    private String closingParagraph(SarDraft.ActivitySummary activity, List<AlertEvidence> alerts) {
        String basis = alerts.size() == 1
                ? "the finding set out above"
                : "the %d findings set out above, taken together".formatted(alerts.size());

        return ("CONCLUSION: on the basis of %s, the activity has no apparent lawful purpose or "
                + "economic rationale consistent with the customer's known profile, and the bank is "
                + "unable to satisfy itself as to the legitimate origin or destination of the funds. "
                + "This report is submitted for the attention of the Financial Intelligence Unit. "
                + "No account has been closed and no funds have been frozen at the time of filing; "
                + "the bank awaits guidance. All supporting records are retained and available on "
                + "request.")
                .formatted(basis);
    }

    /**
     * Recommended action, derived from the case's own disposition rather than
     * invented — the draft must never assert a conclusion the analyst has not
     * actually reached.
     */
    public String recommendAction(String caseDisposition, int highestRiskScore) {
        if (caseDisposition == null) {
            return "Case is not yet disposed. Complete the investigation and record a disposition "
                 + "before filing; this draft is provisional.";
        }
        return switch (caseDisposition) {
            case "ESCALATED_TO_SAR" -> "File this report with the Financial Intelligence Unit within "
                    + "the statutory deadline. Apply enhanced monitoring to the subject's remaining "
                    + "accounts and refer for a relationship review.";
            case "TRUE_POSITIVE" -> "Suspicion confirmed by the analyst but not yet escalated for "
                    + "filing. Refer to a senior analyst for a filing decision.";
            case "FALSE_POSITIVE" -> "The analyst assessed this activity as a false positive. "
                    + "This draft should NOT be filed; it is retained for audit purposes only.";
            case "CLEARED_NO_ACTION" -> "The analyst cleared this activity with no action. "
                    + "This draft should NOT be filed; it is retained for audit purposes only.";
            default -> "Review the case disposition before deciding whether to file.";
        };
    }
}
