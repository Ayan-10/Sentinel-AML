package com.meridiantrust.sentinel.ingestion.model;

import com.meridiantrust.sentinel.account.model.Account;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared lookup state for validating one batch.
 *
 * <p>Accounts are pre-loaded so referential-integrity checks are map lookups
 * rather than a query per row, and {@code seenTransactionIds} catches
 * duplicates <em>within</em> the file — which a database unique constraint
 * would only surface at flush time, by which point the batch has already been
 * assembled and the offending row is hard to attribute.
 */
public record IngestionContext(Map<String, Account> accountsById,
                               Set<String> seenTransactionIds) {

    public static IngestionContext of(Map<String, Account> accountsById) {
        return new IngestionContext(accountsById, new HashSet<>());
    }

    public Account account(String accountId) {
        return accountsById.get(accountId);
    }

    /** @return true if this id has not been seen before in the batch */
    public boolean markSeen(String transactionId) {
        return seenTransactionIds.add(transactionId);
    }
}
