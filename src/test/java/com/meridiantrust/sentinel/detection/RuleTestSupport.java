package com.meridiantrust.sentinel.detection;

import com.meridiantrust.sentinel.detection.model.RuleContext;

import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.reference.service.RuleConfigProvider;
import com.meridiantrust.sentinel.reference.model.RuleParams;
import com.meridiantrust.sentinel.transaction.model.DailyVolume;
import com.meridiantrust.sentinel.transaction.model.Direction;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fixtures for detection rule tests.
 *
 * <p>Rules are pure functions over {@link RuleContext}, so these tests need no
 * Spring context, no database and no transaction manager — just a list of
 * transactions and a stubbed configuration. That is the payoff of keeping
 * rules side-effect free, and it is why the whole detection suite runs in
 * milliseconds.
 */
public final class RuleTestSupport {

    private RuleTestSupport() {}

    public static final String ACCOUNT = "ACC_000001";
    public static final String CUSTOMER = "CUST_00001";
    public static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 9, 18, 9, 0);

    /** A stubbed provider returning fixed params and weight for one rule. */
    public static RuleConfigProvider configWith(String ruleCode, Map<String, Object> params, int weight) {
        RuleConfigProvider provider = Mockito.mock(RuleConfigProvider.class);
        Mockito.when(provider.paramsFor(ruleCode)).thenReturn(new RuleParams(params));
        Mockito.when(provider.weightOf(ruleCode)).thenReturn(weight);
        Mockito.when(provider.isEnabled(ruleCode)).thenReturn(true);
        return provider;
    }

    public static RuleConfigProvider configWith(String ruleCode, Map<String, Object> params) {
        return configWith(ruleCode, params, 30);
    }

    /** A transaction in base currency (INR), so amount == amountBase. */
    public static Transaction txn(String id, LocalDateTime when, Direction direction, String amount) {
        return txn(id, ACCOUNT, CUSTOMER, when, direction, amount, null, null);
    }

    public static Transaction txn(String id, LocalDateTime when, Direction direction,
                                  String amount, String counterparty, String country) {
        return txn(id, ACCOUNT, CUSTOMER, when, direction, amount, counterparty, country);
    }

    public static Transaction txn(String id, String accountId, String customerId,
                                  LocalDateTime when, Direction direction, String amount,
                                  String counterparty, String country) {
        Transaction t = new Transaction();
        t.setTransactionId(id);
        t.setAccountId(accountId);
        t.setCustomerId(customerId);
        t.setTxnTimestamp(when);
        t.setDirection(direction);
        t.setAmount(new BigDecimal(amount));
        t.setCurrency("INR");
        t.setAmountBase(new BigDecimal(amount));
        t.setFxRateApplied(BigDecimal.ONE);
        t.setCounterpartyName(counterparty);
        t.setCounterpartyCountry(country);
        t.setStatus("POSTED");
        return t;
    }

    /** Context for an account-window rule: the same transactions act as both chunk and window. */
    public static RuleContext accountWindowContext(List<Transaction> transactions) {
        Map<String, List<Transaction>> windows = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getAccountId));
        return new RuleContext(transactions, windows, Map.of(), Map.of(), LocalDateTime.now());
    }

    /** Context for a customer-window rule, carrying an explicit daily-volume history. */
    public static RuleContext customerWindowContext(List<Transaction> chunk,
                                                    List<DailyVolume> history) {
        Map<String, List<DailyVolume>> volumes = history.stream()
                .collect(Collectors.groupingBy(DailyVolume::customerId));
        return new RuleContext(chunk, Map.of(), volumes, Map.of(), LocalDateTime.now());
    }

    public static DailyVolume volume(LocalDateTime day, String total, long count) {
        return new DailyVolume(CUSTOMER, day.toLocalDate(), new BigDecimal(total), count);
    }

    public static Customer customer(String id, com.meridiantrust.sentinel.common.model.RiskRating rating,
                                    boolean pep) {
        Customer c = new Customer();
        c.setCustomerId(id);
        c.setFirstName("Test");
        c.setLastName("Customer");
        c.setRiskRating(rating);
        c.setPoliticallyExposed(pep);
        return c;
    }
}
