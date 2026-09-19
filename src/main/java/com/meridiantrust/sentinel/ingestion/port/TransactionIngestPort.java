package com.meridiantrust.sentinel.ingestion.port;

import com.meridiantrust.sentinel.ingestion.model.IngestionResult;
import com.meridiantrust.sentinel.ingestion.model.RawTransaction;

import java.util.List;

/**
 * The inbound port for transaction ingestion (hexagonal architecture).
 *
 * <p>Every source — CSV upload, REST batch, single streaming POST — converts
 * its own wire format into {@link RawTransaction} and calls this one contract.
 * The core therefore has no idea where a transaction came from.
 *
 * <p>That is what makes the Kafka extension in the problem statement's
 * extension ideas a genuinely additive change rather than a rewrite: a
 * {@code KafkaTransactionAdapter} would deserialise its messages, build
 * {@code RawTransaction}s, and call this method. No validator, no rule, no
 * scoring code and no persistence code would change. The seam is here, today,
 * even though we are not building Kafka in this phase.
 */
public interface TransactionIngestPort {

    /**
     * @param runDetection whether to evaluate detection rules over the accepted
     *                     records. Bulk reference loads set this false and run
     *                     detection as a separate step; live traffic sets it true.
     */
    IngestionResult ingest(List<RawTransaction> records, String source, boolean runDetection);
}
