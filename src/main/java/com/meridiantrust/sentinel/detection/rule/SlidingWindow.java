package com.meridiantrust.sentinel.detection.rule;

import com.meridiantrust.sentinel.transaction.model.Transaction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds clusters of transactions falling inside a rolling time window.
 *
 * <p>Both structuring (business rule 2) and the round-amount typology ask the
 * same structural question — "are there at least N qualifying transactions
 * within H hours?" — differing only in what qualifies. Extracting the window
 * arithmetic here means the two rules cannot drift apart, and the tricky part
 * (window boundaries, cluster non-overlap) is tested once.
 */
public final class SlidingWindow {

    private SlidingWindow() {}

    /**
     * Returns non-overlapping clusters of at least {@code minCount} transactions
     * that fit inside {@code windowHours}.
     *
     * <p>Clusters are non-overlapping by design. Overlapping clusters would
     * report the same underlying pattern several times — five transactions in a
     * day would yield three "structuring" findings rather than one — which is
     * exactly the redundant-alert behaviour the brief requires us to avoid.
     * Deduplication then handles repeat detection across runs; this handles it
     * within a single run.
     *
     * <p>The window is inclusive of both endpoints: transactions exactly
     * {@code windowHours} apart are considered to be within the window, which
     * matches how "within a 24-hour window" is read by compliance.
     *
     * @param candidates transactions already filtered to those that qualify
     */
    public static List<List<Transaction>> findClusters(List<Transaction> candidates,
                                                       int windowHours,
                                                       int minCount) {
        if (candidates == null || candidates.size() < minCount || minCount <= 0) {
            return List.of();
        }

        List<Transaction> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(Transaction::getTxnTimestamp));

        Duration window = Duration.ofHours(windowHours);
        List<List<Transaction>> clusters = new ArrayList<>();

        int start = 0;
        while (start <= sorted.size() - minCount) {
            int end = start;
            // Extend while the next transaction still fits within the window
            // measured from the cluster's first transaction.
            while (end + 1 < sorted.size()
                    && Duration.between(sorted.get(start).getTxnTimestamp(),
                                        sorted.get(end + 1).getTxnTimestamp())
                               .compareTo(window) <= 0) {
                end++;
            }
            int count = end - start + 1;
            if (count >= minCount) {
                clusters.add(List.copyOf(sorted.subList(start, end + 1)));
                start = end + 1;   // non-overlapping: resume after this cluster
            } else {
                start++;
            }
        }
        return clusters;
    }
}
