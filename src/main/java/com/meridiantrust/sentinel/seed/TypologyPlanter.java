package com.meridiantrust.sentinel.seed;

import com.meridiantrust.sentinel.account.model.Account;
import com.meridiantrust.sentinel.ingestion.model.RawTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Plants known laundering typologies into the synthetic dataset.
 *
 * <p>Deliverable D3 requires the seed data to cover at least three typologies.
 * Each method here constructs the activity pattern one detection rule is
 * designed to catch, so the demo has a guaranteed, inspectable case for every
 * rule — and so a reviewer can check the engine catches what it claims rather
 * than trusting the alert count.
 *
 * <p>Each planted scenario is described in {@code docs/DEMO.md} with the
 * customer and account it targets, making the walkthrough reproducible.
 */
@Component
public class TypologyPlanter {

    /** A planted scenario and the rule it is designed to trigger. */
    public record PlantedScenario(String label, String expectedRule, String customerId,
                                  String accountId, List<RawTransaction> transactions) {}

    public List<PlantedScenario> plantAll(List<Account> accounts, LocalDateTime now) {
        List<PlantedScenario> scenarios = new ArrayList<>();
        scenarios.add(structuring(accounts.get(41), now));
        scenarios.add(layering(accounts.get(117), now));
        scenarios.add(sanctionedJurisdiction(accounts.get(203), now));
        scenarios.add(behaviouralSpike(accounts.get(311), now));
        scenarios.add(roundAmounts(accounts.get(388), now));
        scenarios.add(ctrBreachInForeignCurrency(accounts.get(455), now));
        return scenarios;
    }

    /**
     * <b>Structuring.</b> Five deposits between 9,200 and 9,850 inside 18
     * hours — each individually below the 10,000 reporting threshold, together
     * well above it. The signature of someone who knows where the threshold is.
     */
    private PlantedScenario structuring(Account account, LocalDateTime now) {
        LocalDateTime start = now.minusDays(1).withHour(8).withMinute(15);
        BigDecimal[] amounts = {
                new BigDecimal("9200.00"), new BigDecimal("9450.00"), new BigDecimal("9850.00"),
                new BigDecimal("9300.00"), new BigDecimal("9700.00")};

        List<RawTransaction> txns = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            txns.add(txn("TXN_STRUCT_%02d".formatted(i + 1), account,
                    start.plusHours(i * 3L + 1), "CREDIT", amounts[i], "INR",
                    "BRANCH", "CASH_DEPOSIT", "Cash Deposit - Counter", "IN",
                    "Cash deposit"));
        }
        return new PlantedScenario("Structuring / smurfing", "STRUCTURING",
                account.getCustomerId(), account.getAccountId(), txns);
    }

    /**
     * <b>Layering.</b> A large deposit, then 92% of it dispersed to four
     * different counterparties within 31 hours. Funds passing straight through
     * rather than accumulating.
     */
    private PlantedScenario layering(Account account, LocalDateTime now) {
        LocalDateTime deposit = now.minusDays(2).withHour(10).withMinute(5);
        List<RawTransaction> txns = new ArrayList<>();

        txns.add(txn("TXN_LAYER_IN", account, deposit, "CREDIT",
                new BigDecimal("850000.00"), "INR", "RTGS", "TRANSFER_IN",
                "Meridian Exports Pvt Ltd", "IN", "Contract settlement"));

        String[] recipients = {"Orion Trade Partners Ltd", "Pyramid Capital SA",
                               "Silverline Commodities Ltd", "Zenith Holdings FZE"};
        String[] countries = {"VG", "PA", "KY", "AE"};
        BigDecimal[] amounts = {
                new BigDecimal("240000.00"), new BigDecimal("215000.00"),
                new BigDecimal("190000.00"), new BigDecimal("137000.00")};

        for (int i = 0; i < recipients.length; i++) {
            txns.add(txn("TXN_LAYER_OUT_%d".formatted(i + 1), account,
                    deposit.plusHours(6L + i * 8L), "DEBIT", amounts[i], "INR",
                    "SWIFT", "TRANSFER_OUT", recipients[i], countries[i],
                    "Onward transfer"));
        }
        return new PlantedScenario("Rapid movement / layering", "RAPID_MOVEMENT",
                account.getCustomerId(), account.getAccountId(), txns);
    }

    /**
     * <b>Jurisdiction risk.</b> Includes a deliberately tiny 850-rupee transfer
     * to a sanctioned entity. Business rule 4 requires an alert regardless of
     * amount, and a small "test" transfer is exactly how an operator verifies a
     * channel before moving real money — so this is the case that proves the
     * rule applies no threshold.
     */
    private PlantedScenario sanctionedJurisdiction(Account account, LocalDateTime now) {
        List<RawTransaction> txns = new ArrayList<>();
        txns.add(txn("TXN_SANCTION_01", account, now.minusDays(3).withHour(14),
                "DEBIT", new BigDecimal("850.00"), "INR", "SWIFT", "TRANSFER_OUT",
                "Delta Bridge Exchange", "IR", "Test transfer"));
        txns.add(txn("TXN_SANCTION_02", account, now.minusDays(3).withHour(16),
                "DEBIT", new BigDecimal("425000.00"), "INR", "SWIFT", "TRANSFER_OUT",
                "Delta Bridge Exchange", "IR", "Trade settlement"));
        txns.add(txn("TXN_SANCTION_03", account, now.minusDays(2).withHour(11),
                "DEBIT", new BigDecimal("310000.00"), "INR", "SWIFT", "TRANSFER_OUT",
                "Kestrel Logistics OOO", "RU", "Logistics invoice"));
        return new PlantedScenario("High-risk / sanctioned jurisdiction", "HIGH_RISK_JURISDICTION",
                account.getCustomerId(), account.getAccountId(), txns);
    }

    /**
     * <b>Behavioural deviation.</b> Ninety days of a steady ~15,000/day pattern,
     * then a single 620,000 day. The history is essential — without it the
     * baseline guard correctly refuses to alert, which is the behaviour a
     * cold-start customer should get.
     */
    private PlantedScenario behaviouralSpike(Account account, LocalDateTime now) {
        List<RawTransaction> txns = new ArrayList<>();
        int seq = 1;
        for (int day = 90; day >= 3; day -= 2) {
            txns.add(txn("TXN_BASE_%03d".formatted(seq++), account,
                    now.minusDays(day).withHour(11).withMinute(30), "DEBIT",
                    new BigDecimal("15000.00"), "INR", "UPI", "PURCHASE",
                    "Bigbasket", "IN", "Routine spend"));
        }
        LocalDateTime spikeDay = now.minusDays(1).withHour(9);
        BigDecimal[] spike = {
                new BigDecimal("185000.00"), new BigDecimal("210000.00"),
                new BigDecimal("225000.00")};
        for (int i = 0; i < spike.length; i++) {
            txns.add(txn("TXN_SPIKE_%d".formatted(i + 1), account,
                    spikeDay.plusHours(i * 2L), "DEBIT", spike[i], "INR",
                    "NEFT", "TRANSFER_OUT", "Horizon Ventures LLP", "IN",
                    "Business payment"));
        }
        return new PlantedScenario("Behavioural deviation", "BEHAVIORAL_DEVIATION",
                account.getCustomerId(), account.getAccountId(), txns);
    }

    /** <b>Round-number pattern.</b> Six exact multiples of 50,000 in one day. */
    private PlantedScenario roundAmounts(Account account, LocalDateTime now) {
        LocalDateTime start = now.minusDays(1).withHour(9).withMinute(0);
        BigDecimal[] amounts = {
                new BigDecimal("50000.00"), new BigDecimal("100000.00"),
                new BigDecimal("50000.00"), new BigDecimal("150000.00"),
                new BigDecimal("100000.00"), new BigDecimal("50000.00")};

        List<RawTransaction> txns = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            txns.add(txn("TXN_ROUND_%02d".formatted(i + 1), account,
                    start.plusHours(i * 2L), "DEBIT", amounts[i], "INR",
                    "NEFT", "TRANSFER_OUT", "Apex Trading Co", "IN", "Payment"));
        }
        return new PlantedScenario("Repeated round amounts", "ROUND_AMOUNT_PATTERN",
                account.getCustomerId(), account.getAccountId(), txns);
    }

    /**
     * <b>CTR threshold via FX.</b> A 2,400 USD transfer — below 10,000 as a raw
     * number, but roughly 200,000 INR once normalised. It proves business rule 9
     * end to end: without base-currency normalisation this transaction would
     * pass a 10,000 threshold unexamined.
     */
    private PlantedScenario ctrBreachInForeignCurrency(Account account, LocalDateTime now) {
        List<RawTransaction> txns = new ArrayList<>();
        txns.add(txn("TXN_FX_CTR_01", account, now.minusDays(1).withHour(13),
                "CREDIT", new BigDecimal("2400.00"), "USD", "SWIFT", "TRANSFER_IN",
                "Overseas Consulting Inc", "US", "Consulting fee (USD)"));
        txns.add(txn("TXN_FX_CTR_02", account, now.minusDays(1).withHour(15),
                "CREDIT", new BigDecimal("1800.00"), "EUR", "SWIFT", "TRANSFER_IN",
                "Continental Partners GmbH", "DE", "Service fee (EUR)"));
        txns.add(txn("TXN_CTR_03", account, now.minusHours(20),
                "DEBIT", new BigDecimal("475000.00"), "INR", "RTGS", "TRANSFER_OUT",
                "Sterling Realty Pvt Ltd", "IN", "Property advance"));
        return new PlantedScenario("CTR threshold (incl. post-FX breach)", "CTR_THRESHOLD",
                account.getCustomerId(), account.getAccountId(), txns);
    }

    private RawTransaction txn(String id, Account account, LocalDateTime timestamp,
                               String direction, BigDecimal amount, String currency,
                               String channel, String type, String counterparty,
                               String country, String description) {
        return new RawTransaction(id, account.getAccountId(), account.getCustomerId(),
                timestamp.toString(), direction, amount.toPlainString(), currency,
                channel, type, counterparty, "CP" + Math.abs(id.hashCode() % 100000000),
                "International Bank", country, description, "POSTED", "seed-typology");
    }
}
