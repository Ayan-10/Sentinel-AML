package com.meridiantrust.sentinel.detection.service;

import com.meridiantrust.sentinel.detection.model.RuleContext;

import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.customer.repository.CustomerRepository;
import com.meridiantrust.sentinel.reference.service.RuleConfigProvider;
import com.meridiantrust.sentinel.transaction.model.DailyVolume;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import com.meridiantrust.sentinel.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a {@link RuleContext} for a chunk of transactions.
 *
 * <p><b>This class is the performance design.</b> The obvious implementation of
 * a window-based rule is to query each transaction's history as it is
 * evaluated, which costs O(transactions x rules) database round trips — for
 * 10,000 transactions and six rules, tens of thousands of queries, and the
 * two-minute target is gone.
 *
 * <p>Instead the loader inspects the whole chunk first, collects the distinct
 * accounts and customers, and issues a <em>fixed</em> number of batched
 * queries — three, regardless of chunk size. Rules then read from in-memory
 * maps. Query count drops from O(n) to O(1) per chunk.
 *
 * <p>The lookback is derived from the rules themselves
 * ({@link DetectionEngine#maxLookbackHours()}) rather than hardcoded, so
 * widening a rule's window through the admin API automatically widens the data
 * loaded for it. A hardcoded lookback would let an operator retune a window to
 * 72 hours and silently get 48 hours of data — a rule that appears configured
 * but is quietly capped.
 */
@Component
public class RuleWindowLoader {

    private static final Logger log = LoggerFactory.getLogger(RuleWindowLoader.class);

    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final RuleConfigProvider configProvider;

    public RuleWindowLoader(TransactionRepository transactionRepository,
                            CustomerRepository customerRepository,
                            RuleConfigProvider configProvider) {
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.configProvider = configProvider;
    }

    @Transactional(readOnly = true)
    public RuleContext load(List<Transaction> chunk, int lookbackHours) {
        if (chunk.isEmpty()) {
            return RuleContext.of(List.of());
        }

        Set<String> accountIds = chunk.stream()
                .map(Transaction::getAccountId).collect(Collectors.toSet());
        Set<String> customerIds = chunk.stream()
                .map(Transaction::getCustomerId).collect(Collectors.toSet());

        LocalDateTime earliest = chunk.stream()
                .map(Transaction::getTxnTimestamp)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        LocalDateTime latest = chunk.stream()
                .map(Transaction::getTxnTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        // Query 1: account windows. Reaches back far enough for the widest
        // configured account-scoped rule, and forward to cover rapid movement's
        // look-ahead from a deposit at the end of the chunk.
        LocalDateTime windowFrom = earliest.minusHours(lookbackHours);
        LocalDateTime windowTo = latest.plusHours(lookbackHours);
        Map<String, List<Transaction>> accountWindows =
                transactionRepository.findWindowByAccounts(accountIds, windowFrom, windowTo)
                        .stream()
                        .collect(Collectors.groupingBy(Transaction::getAccountId,
                                Collectors.toCollection(ArrayList::new)));
        accountWindows.values().forEach(list ->
                list.sort(Comparator.comparing(Transaction::getTxnTimestamp)));

        // Query 2: behavioural baselines, aggregated by the database.
        int baselineDays = configProvider
                .paramsFor("BEHAVIORAL_DEVIATION")
                .getInt("baselineDays", 90);
        Map<String, List<DailyVolume>> dailyVolumes =
                transactionRepository.findDailyVolumes(customerIds,
                                earliest.minusDays(baselineDays).toLocalDate().atStartOfDay(),
                                latest.toLocalDate().atTime(23, 59, 59))
                        .stream()
                        .collect(Collectors.groupingBy(DailyVolume::customerId));

        // Query 3: customer records, for risk-rating and PEP score uplifts.
        Map<String, Customer> customers = customerRepository.findAllByIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getCustomerId, Function.identity(), (a, b) -> a));

        log.debug("Loaded detection context: {} txns, {} accounts, {} customers, lookback {}h",
                chunk.size(), accountWindows.size(), customers.size(), lookbackHours);

        return new RuleContext(chunk, accountWindows, dailyVolumes, customers, LocalDateTime.now());
    }
}
