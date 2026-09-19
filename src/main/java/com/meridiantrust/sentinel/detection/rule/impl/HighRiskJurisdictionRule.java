package com.meridiantrust.sentinel.detection.rule.impl;

import com.meridiantrust.sentinel.common.model.Formatting;
import com.meridiantrust.sentinel.detection.model.*;
import com.meridiantrust.sentinel.detection.rule.*;
import com.meridiantrust.sentinel.detection.service.*;
import com.meridiantrust.sentinel.reference.model.JurisdictionMatch;
import com.meridiantrust.sentinel.reference.service.ReferenceDataProvider;
import com.meridiantrust.sentinel.reference.service.RuleConfigProvider;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <b>Business rule 4</b> — transactions involving counterparties or
 * jurisdictions on the high-risk / sanctions list <em>always</em> generate an
 * alert, regardless of amount.
 *
 * <p>"Regardless of amount" is the whole point and is easy to get wrong. A
 * 500-rupee test transfer to a sanctioned entity is not a trivial transaction —
 * it is how an operator verifies a channel before moving real money. So this
 * rule applies no amount threshold whatsoever, and a test asserts that a
 * one-rupee transfer to a sanctioned jurisdiction still alerts.
 *
 * <p>Two independent match paths: the counterparty <em>country</em> against the
 * jurisdiction list, and the counterparty <em>name</em> against the named
 * watchlist. A designated entity operating from a clean jurisdiction is caught
 * by the second even when the first is silent.
 */
@Component
public class HighRiskJurisdictionRule extends AbstractDetectionRule {

    public static final String CODE = "HIGH_RISK_JURISDICTION";

    private final ReferenceDataProvider referenceData;

    public HighRiskJurisdictionRule(RuleConfigProvider configProvider,
                                    ReferenceDataProvider referenceData) {
        super(configProvider);
        this.referenceData = referenceData;
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public String typology() {
        return "JURISDICTION_RISK";
    }

    @Override
    public RuleScope scope() {
        return RuleScope.TRANSACTION;
    }

    @Override
    public List<RuleHit> evaluate(RuleContext context) {
        List<RuleHit> hits = new ArrayList<>();

        for (Transaction txn : context.transactions()) {
            Optional<JurisdictionMatch> countryMatch =
                    referenceData.matchCountry(txn.getCounterpartyCountry());
            Optional<JurisdictionMatch> partyMatch =
                    referenceData.matchCounterparty(txn.getCounterpartyName());

            if (countryMatch.isEmpty() && partyMatch.isEmpty()) {
                continue;
            }

            // Where both match, the higher-weighted determines the uplift.
            JurisdictionMatch primary = countryMatch
                    .filter(c -> partyMatch.isEmpty() || c.riskWeight() >= partyMatch.get().riskWeight())
                    .orElseGet(() -> partyMatch.orElseThrow());

            String dedupKey = dedupKeyForTransaction(txn.getTransactionId());
            String explanation = buildExplanation(txn, countryMatch, partyMatch);

            hits.add(hit()
                    .customerId(txn.getCustomerId())
                    .accountId(txn.getAccountId())
                    .dedupKey(dedupKey)
                    .explanation(explanation)
                    .evidenceTransactionIds(List.of(txn.getTransactionId()))
                    .evidenceAmountBase(txn.getAmountBase())
                    .jurisdictionUplift(primary.riskWeight())
                    .build());
            traceHit(dedupKey, explanation);
        }
        return hits;
    }

    private String buildExplanation(Transaction txn,
                                    Optional<JurisdictionMatch> country,
                                    Optional<JurisdictionMatch> party) {
        StringBuilder sb = new StringBuilder();
        sb.append("Transaction %s (%s, %s) on account %s ".formatted(
                txn.getTransactionId(),
                Formatting.money(txn.getAmountBase()),
                Formatting.timestamp(txn.getTxnTimestamp()),
                txn.getAccountId()));

        country.ifPresent(c -> sb.append("involves %s, listed as %s (source: %s). ".formatted(
                c.matchedValue(), c.category(), c.source() == null ? "internal list" : c.source())));

        party.ifPresent(p -> sb.append("Counterparty '%s' appears on the %s watchlist%s. ".formatted(
                p.matchedValue(), p.category(),
                p.source() == null || p.source().isBlank() ? "" : " — " + p.source())));

        sb.append("Business rule 4 requires an alert regardless of transaction value.");
        return sb.toString();
    }
}
