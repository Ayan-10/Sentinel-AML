package com.meridiantrust.sentinel.detection.model;

import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.transaction.model.DailyVolume;
import com.meridiantrust.sentinel.transaction.model.Transaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a rule needs to evaluate a chunk of transactions — and nothing
 * else.
 *
 * <p>This type is why detection rules are pure functions. All history, all
 * reference data and all customer context arrive here, pre-loaded, so
 * {@code evaluate()} performs no I/O and holds no state. The consequences are
 * worth stating plainly:
 *
 * <ul>
 *   <li><b>Testability</b> — a rule test constructs a context from a list of
 *       transactions. No Spring context, no database, no mocking framework
 *       beyond a config stub. Milliseconds per test.</li>
 *   <li><b>Thread safety</b> — rules are stateless singletons and this context
 *       is immutable, so parallel chunk evaluation needs no locking at all.</li>
 *   <li><b>Performance</b> — windows are loaded once per chunk rather than once
 *       per transaction.</li>
 * </ul>
 *
 * @param transactions   the transactions under evaluation in this chunk
 * @param accountWindows per account, its transactions over the lookback window,
 *                       ascending by timestamp
 * @param dailyVolumes   per customer, historical daily totals for the baseline
 * @param customers      customer records for the chunk, for risk-rating context
 * @param evaluatedAt    logical evaluation time
 */
public record RuleContext(
        List<Transaction> transactions,
        Map<String, List<Transaction>> accountWindows,
        Map<String, List<DailyVolume>> dailyVolumes,
        Map<String, Customer> customers,
        LocalDateTime evaluatedAt) {

    public RuleContext {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
        accountWindows = accountWindows == null ? Map.of() : Map.copyOf(accountWindows);
        dailyVolumes = dailyVolumes == null ? Map.of() : Map.copyOf(dailyVolumes);
        customers = customers == null ? Map.of() : Map.copyOf(customers);
    }

    /** Convenience factory for transaction-scoped unit tests. */
    public static RuleContext of(List<Transaction> transactions) {
        return new RuleContext(transactions, Map.of(), Map.of(), Map.of(), LocalDateTime.now());
    }

    public List<Transaction> windowFor(String accountId) {
        return accountWindows.getOrDefault(accountId, List.of());
    }

    public List<DailyVolume> baselineFor(String customerId) {
        return dailyVolumes.getOrDefault(customerId, List.of());
    }

    public Optional<Customer> customer(String customerId) {
        return Optional.ofNullable(customers.get(customerId));
    }
}
