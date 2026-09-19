package com.meridiantrust.sentinel.transaction.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Per-customer, per-day aggregate backing the business rule 5 baseline.
 *
 * <p>Computed by the database rather than by loading 90 days of transactions
 * into memory per customer — the difference between a query and an OOM at
 * realistic volumes.
 */
public record DailyVolume(String customerId, LocalDate day, BigDecimal totalBase, Long txnCount) {
}
