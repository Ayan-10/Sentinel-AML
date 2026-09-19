package com.meridiantrust.sentinel.detection.service;

import com.meridiantrust.sentinel.detection.model.DetectionResult;
import com.meridiantrust.sentinel.detection.model.RuleContext;
import com.meridiantrust.sentinel.detection.model.RuleHit;

import com.meridiantrust.sentinel.alerting.service.AlertService;
import com.meridiantrust.sentinel.common.config.AsyncConfig;
import com.meridiantrust.sentinel.common.config.SentinelProperties;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates detection over a set of transactions.
 *
 * <p>Two execution profiles share one code path:
 * <ul>
 *   <li><b>Bulk</b> — chunked and evaluated in parallel. NFR: 10,000
 *       transactions in under two minutes.</li>
 *   <li><b>Streaming</b> — a single transaction evaluated synchronously so the
 *       caller learns within the same HTTP request whether it alerted. NFR:
 *       sub-second latency.</li>
 * </ul>
 *
 * <p><b>Why chunks are partitioned by account:</b> the account-scoped rules
 * (structuring, rapid movement, round amounts) reason over an account's
 * transactions as a sequence. Splitting one account's activity across two
 * chunks evaluated by different threads would let a structuring cluster
 * straddle the boundary and go undetected — a lost alert, which the concurrency
 * NFR explicitly forbids. Sorting by account before partitioning keeps each
 * account's activity contiguous, and the window loader then reaches back into
 * history so a cluster spanning chunks is still seen whole.
 */
@Service
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    private final DetectionEngine engine;
    private final RuleWindowLoader windowLoader;
    private final AlertService alertService;
    private final ThreadPoolTaskExecutor executor;
    private final int chunkSize;

    public DetectionService(DetectionEngine engine,
                            RuleWindowLoader windowLoader,
                            AlertService alertService,
                            @Qualifier(AsyncConfig.DETECTION_EXECUTOR) ThreadPoolTaskExecutor executor,
                            SentinelProperties properties) {
        this.engine = engine;
        this.windowLoader = windowLoader;
        this.alertService = alertService;
        this.executor = executor;
        this.chunkSize = properties.detection().bulkChunkSize();
    }

    /** Bulk path — parallel chunk evaluation. */
    public DetectionResult runBulk(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return DetectionResult.empty();
        }
        long start = System.currentTimeMillis();
        int lookbackHours = engine.maxLookbackHours();

        List<Transaction> ordered = new ArrayList<>(transactions);
        ordered.sort(Comparator.comparing(Transaction::getAccountId)
                .thenComparing(Transaction::getTxnTimestamp));

        List<List<Transaction>> chunks = partitionByAccount(ordered, chunkSize);
        AtomicInteger hits = new AtomicInteger();
        AtomicInteger created = new AtomicInteger();

        List<CompletableFuture<Void>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.runAsync(() -> {
                    RuleContext context = windowLoader.load(chunk, lookbackHours);
                    List<RuleHit> chunkHits = engine.evaluate(context);
                    hits.addAndGet(chunkHits.size());
                    created.addAndGet(alertService.persistHits(chunkHits, context.customers()));
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long duration = System.currentTimeMillis() - start;
        log.info("Bulk detection complete: {} transactions, {} chunks, {} hits, {} new alerts in {} ms",
                ordered.size(), chunks.size(), hits.get(), created.get(), duration);
        return new DetectionResult(ordered.size(), hits.get(), created.get(), duration);
    }

    /** Streaming path — synchronous, so alerts are known before the response returns. */
    public DetectionResult runStreaming(Transaction transaction) {
        long start = System.currentTimeMillis();
        RuleContext context = windowLoader.load(List.of(transaction), engine.maxLookbackHours());
        List<RuleHit> hits = engine.evaluate(context);
        int created = alertService.persistHits(hits, context.customers());
        long duration = System.currentTimeMillis() - start;
        log.debug("Streaming detection for {}: {} hits, {} alerts in {} ms",
                transaction.getTransactionId(), hits.size(), created, duration);
        return new DetectionResult(1, hits.size(), created, duration);
    }

    /**
     * Splits into chunks of roughly {@code target} size without ever cutting an
     * account's transactions across a boundary.
     *
     * <p>The chunk is allowed to overshoot the target rather than split an
     * account. Correctness of account-scoped rules outranks chunk uniformity:
     * an oversized chunk costs a little latency, a split account costs a missed
     * alert.
     */
    private List<List<Transaction>> partitionByAccount(List<Transaction> ordered, int target) {
        List<List<Transaction>> chunks = new ArrayList<>();
        List<Transaction> current = new ArrayList<>();
        String currentAccount = null;

        for (Transaction txn : ordered) {
            boolean accountChanged = !txn.getAccountId().equals(currentAccount);
            if (accountChanged && current.size() >= target) {
                chunks.add(List.copyOf(current));
                current = new ArrayList<>();
            }
            current.add(txn);
            currentAccount = txn.getAccountId();
        }
        if (!current.isEmpty()) {
            chunks.add(List.copyOf(current));
        }
        return chunks;
    }
}
