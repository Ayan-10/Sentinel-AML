package com.meridiantrust.sentinel.ingestion.model;

/**
 * An unvalidated inbound transaction, as supplied by any ingestion adapter.
 *
 * <p>Every field is a {@code String}. That is intentional: parsing is a
 * validation concern, and a record that cannot hold "2026-13-45" or "abc" as an
 * amount cannot report those as row-level rejections — it fails at the parser
 * and takes the whole file with it. Keeping the raw form lets the validation
 * chain reject one row and continue.
 */
public record RawTransaction(
        String transactionId,
        String accountId,
        String customerId,
        String txnTimestamp,
        String direction,
        String amount,
        String currency,
        String channel,
        String txnType,
        String counterpartyName,
        String counterpartyAccount,
        String counterpartyBank,
        String counterpartyCountry,
        String description,
        String status,
        String rawLine) {
}
