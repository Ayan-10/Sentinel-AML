package com.meridiantrust.sentinel.detection.rules;

import com.meridiantrust.sentinel.detection.rule.impl.HighRiskJurisdictionRule;

import com.meridiantrust.sentinel.detection.model.RuleContext;
import com.meridiantrust.sentinel.detection.model.RuleHit;
import com.meridiantrust.sentinel.reference.model.JurisdictionMatch;
import com.meridiantrust.sentinel.reference.service.ReferenceDataProvider;
import com.meridiantrust.sentinel.transaction.model.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.meridiantrust.sentinel.detection.RuleTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business rule 4: transactions involving listed jurisdictions or counterparties
 * alert <em>regardless of amount</em>.
 *
 * <p>The amount-independence test is the important one. Every other rule in the
 * system has a threshold, so the natural instinct when writing this rule is to
 * add one — and a small "test" transfer is precisely how an operator verifies a
 * channel before moving real money.
 */
class HighRiskJurisdictionRuleTest {

    private ReferenceDataProvider referenceData;
    private HighRiskJurisdictionRule rule;

    @BeforeEach
    void setUp() {
        referenceData = Mockito.mock(ReferenceDataProvider.class);
        Mockito.when(referenceData.matchCountry(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(referenceData.matchCounterparty(Mockito.anyString())).thenReturn(Optional.empty());
        rule = new HighRiskJurisdictionRule(
                configWith(HighRiskJurisdictionRule.CODE, Map.of()), referenceData);
    }

    private void listCountry(String code, String name, String category, int weight) {
        Mockito.when(referenceData.matchCountry(code)).thenReturn(Optional.of(
                new JurisdictionMatch(JurisdictionMatch.TYPE_COUNTRY, name, category, weight,
                        "FATF Call for Action")));
    }

    @Test
    @DisplayName("a one-rupee transfer to a sanctioned country still alerts — no amount threshold")
    void tinyAmountStillAlerts() {
        listCountry("IR", "Iran", "SANCTIONED", 40);
        RuleContext ctx = RuleContext.of(List.of(
                txn("T1", BASE_TIME, Direction.DEBIT, "1.00", "Some Entity", "IR")));

        List<RuleHit> hits = rule.evaluate(ctx);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).jurisdictionUplift()).isEqualTo(40);
        assertThat(hits.get(0).explanation()).contains("regardless of transaction value");
    }

    @Test
    @DisplayName("a transaction to an unlisted country does not alert")
    void unlistedCountryDoesNotAlert() {
        RuleContext ctx = RuleContext.of(List.of(
                txn("T1", BASE_TIME, Direction.DEBIT, "5000000.00", "Reliance Retail", "IN")));

        assertThat(rule.evaluate(ctx)).isEmpty();
    }

    @Test
    @DisplayName("a watchlisted counterparty alerts even from a clean jurisdiction")
    void watchlistedCounterpartyAlerts() {
        Mockito.when(referenceData.matchCounterparty("Delta Bridge Exchange"))
                .thenReturn(Optional.of(new JurisdictionMatch(
                        JurisdictionMatch.TYPE_COUNTERPARTY, "Delta Bridge Exchange",
                        "SANCTIONS", 40, "Designated entity")));

        RuleContext ctx = RuleContext.of(List.of(
                txn("T1", BASE_TIME, Direction.DEBIT, "2500.00", "Delta Bridge Exchange", "GB")));

        List<RuleHit> hits = rule.evaluate(ctx);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).explanation()).contains("Delta Bridge Exchange");
    }

    @Test
    @DisplayName("when country and counterparty both match, the higher risk weight applies")
    void higherWeightWins() {
        listCountry("AE", "United Arab Emirates", "MONITORED", 15);
        Mockito.when(referenceData.matchCounterparty("Zenith Holdings FZE"))
                .thenReturn(Optional.of(new JurisdictionMatch(
                        JurisdictionMatch.TYPE_COUNTERPARTY, "Zenith Holdings FZE",
                        "SHELL_COMPANY", 30, "Opaque ownership")));

        RuleContext ctx = RuleContext.of(List.of(
                txn("T1", BASE_TIME, Direction.DEBIT, "100000.00", "Zenith Holdings FZE", "AE")));

        List<RuleHit> hits = rule.evaluate(ctx);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).jurisdictionUplift()).isEqualTo(30);
        // Both matches are reported, because an analyst needs the full picture.
        assertThat(hits.get(0).explanation())
                .contains("United Arab Emirates")
                .contains("Zenith Holdings FZE");
    }

    @Test
    @DisplayName("a transaction with no counterparty country is handled without error")
    void nullCountryIsSafe() {
        RuleContext ctx = RuleContext.of(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "1000.00", null, null)));

        assertThat(rule.evaluate(ctx)).isEmpty();
    }
}
