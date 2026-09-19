package com.meridiantrust.sentinel.ingestion.validation;

import com.meridiantrust.sentinel.ingestion.model.IngestionContext;
import com.meridiantrust.sentinel.ingestion.model.RawTransaction;

import org.springframework.core.Ordered;

/**
 * One link in the ingestion validation chain (Chain of Responsibility).
 *
 * <p>Each validator answers one question and is ordered by cost: cheap
 * structural checks run before lookups, so a malformed row is rejected without
 * touching the account map. Adding a new check — a sanctions pre-screen, a
 * velocity cap — means adding a {@code @Component}; the chain assembles itself
 * from whatever implementations exist, in {@link Ordered} order.
 */
public interface TransactionValidator extends Ordered {

    ValidationOutcome validate(RawTransaction raw, IngestionContext context);
}
