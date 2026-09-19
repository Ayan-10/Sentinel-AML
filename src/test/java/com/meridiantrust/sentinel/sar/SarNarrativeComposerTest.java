package com.meridiantrust.sentinel.sar;

import com.meridiantrust.sentinel.sar.model.AlertEvidence;
import com.meridiantrust.sentinel.sar.model.SarDraft;
import com.meridiantrust.sentinel.sar.service.SarNarrativeComposer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The composer is a pure function from values to prose, so it is tested with no
 * Spring context, no database and no mocks — the same property that makes the
 * detection rules cheap to test.
 */
class SarNarrativeComposerTest {

    private final SarNarrativeComposer composer = new SarNarrativeComposer();

    private SarDraft.Subject subject(boolean pep) {
        return new SarDraft.Subject("CUST_00134", "Karthik Nair", "IDN11061146",
                LocalDate.of(1981, 4, 12), "Bengaluru, Karnataka, IN, 560001",
                "Self-Employed", "PREMIUM", "VERIFIED", "MEDIUM", pep, LocalDate.of(2018, 6, 1));
    }

    private SarDraft.ActivitySummary activity(String disposition, int alertCount) {
        return new SarDraft.ActivitySummary(
                LocalDateTime.of(2026, 9, 16, 14, 49),
                LocalDateTime.of(2026, 9, 17, 11, 0),
                3, new BigDecimal("735850.00"), "INR", alertCount, 77, disposition);
    }

    private AlertEvidence finding(String ref, String typology, int score) {
        return new AlertEvidence(ref, "HIGH_RISK_JURISDICTION", typology, score, "HIGH",
                "Transaction TXN_SANCTION_01 involves Iran, listed as SANCTIONED.", 1);
    }

    private final List<SarDraft.AccountSummary> accounts = List.of(
            new SarDraft.AccountSummary("ACC_000204", "CURRENT", "INR",
                    LocalDate.of(2018, 6, 1), "BR122 / Bengaluru"));

    @Test
    @DisplayName("narrative names the institution, subject, period and aggregate value")
    void narrativeCoversTheEssentials() {
        String narrative = composer.compose(subject(false), activity("ESCALATED_TO_SAR", 1),
                accounts, List.of(finding("ALT-1", "JURISDICTION_RISK", 77)));

        assertThat(narrative)
                .contains("MeridianTrust Bank")
                .contains("Karthik Nair")
                .contains("CUST_00134")
                .contains("ACC_000204")
                .contains("735,850.00")
                .contains("16 Sep 2026")
                .contains("17 Sep 2026");
    }

    @Test
    @DisplayName("no format placeholder survives into the narrative")
    void noUnsubstitutedPlaceholders() {
        // Java's `.formatted()` binds to the last literal in a concatenation, so an
        // unparenthesised format string silently ships "%s" to a regulator. Assert
        // against the whole narrative rather than a substring, which is how the
        // original defect slipped past.
        String narrative = composer.compose(subject(true), activity("ESCALATED_TO_SAR", 2), accounts,
                List.of(finding("ALT-1", "JURISDICTION_RISK", 77),
                        finding("ALT-2", "STRUCTURING", 54)));

        assertThat(narrative).doesNotContain("%s").doesNotContain("%d").doesNotContain("%n");
    }

    @Test
    @DisplayName("subject paragraph renders tenure, risk rating and KYC status in the right slots")
    void subjectParagraphSubstitutesCorrectly() {
        String narrative = composer.compose(subject(false), activity("ESCALATED_TO_SAR", 1),
                accounts, List.of(finding("ALT-1", "JURISDICTION_RISK", 77)));

        assertThat(narrative)
                .contains("since 2018-06-01")
                .contains("classified medium risk")
                .contains("KYC status VERIFIED");
    }

    @Test
    @DisplayName("each alert's own explanation is quoted, keeping the narrative traceable")
    void quotesAlertExplanations() {
        String narrative = composer.compose(subject(false), activity("ESCALATED_TO_SAR", 1),
                accounts, List.of(finding("ALT-1", "JURISDICTION_RISK", 77)));

        assertThat(narrative)
                .contains("FINDING ALT-1")
                .contains("Transaction TXN_SANCTION_01 involves Iran, listed as SANCTIONED.");
    }

    @Test
    @DisplayName("a politically exposed subject is called out explicitly")
    void flagsPoliticallyExposedSubject() {
        String withPep = composer.compose(subject(true), activity("ESCALATED_TO_SAR", 1),
                accounts, List.of(finding("ALT-1", "JURISDICTION_RISK", 77)));
        String withoutPep = composer.compose(subject(false), activity("ESCALATED_TO_SAR", 1),
                accounts, List.of(finding("ALT-1", "JURISDICTION_RISK", 77)));

        assertThat(withPep).contains("POLITICALLY EXPOSED PERSON");
        assertThat(withoutPep).doesNotContain("POLITICALLY EXPOSED PERSON");
    }

    @Test
    @DisplayName("multiple findings are all reported and the wording pluralises correctly")
    void handlesMultipleFindings() {
        String narrative = composer.compose(subject(false), activity("ESCALATED_TO_SAR", 3), accounts,
                List.of(finding("ALT-1", "JURISDICTION_RISK", 77),
                        finding("ALT-2", "STRUCTURING", 54),
                        finding("ALT-3", "LAYERING", 61)));

        assertThat(narrative)
                .contains("FINDING ALT-1").contains("FINDING ALT-2").contains("FINDING ALT-3")
                .contains("typologies:")
                .contains("3 findings set out above");
    }

    @Test
    @DisplayName("a single finding uses singular wording")
    void singleFindingUsesSingular() {
        String narrative = composer.compose(subject(false), activity("ESCALATED_TO_SAR", 1),
                accounts, List.of(finding("ALT-1", "JURISDICTION_RISK", 77)));

        assertThat(narrative).contains("typology:").contains("the finding set out above");
    }

    @Test
    @DisplayName("a false-positive case must be told NOT to file — the draft never overstates suspicion")
    void falsePositiveIsNotRecommendedForFiling() {
        assertThat(composer.recommendAction("FALSE_POSITIVE", 40))
                .contains("should NOT be filed");
        assertThat(composer.recommendAction("CLEARED_NO_ACTION", 40))
                .contains("should NOT be filed");
    }

    @Test
    @DisplayName("an escalated case is recommended for filing with a deadline reminder")
    void escalatedCaseIsRecommendedForFiling() {
        assertThat(composer.recommendAction("ESCALATED_TO_SAR", 77))
                .contains("File this report")
                .contains("statutory deadline");
    }

    @Test
    @DisplayName("an undisposed case yields a provisional draft, not a filing recommendation")
    void undisposedCaseIsProvisional() {
        assertThat(composer.recommendAction(null, 60))
                .contains("not yet disposed")
                .contains("provisional");
    }
}
