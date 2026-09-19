package com.meridiantrust.sentinel.ingestion.validation;

import com.meridiantrust.sentinel.ingestion.model.IngestionContext;
import com.meridiantrust.sentinel.ingestion.model.RawTransaction;
import com.meridiantrust.sentinel.ingestion.util.TimestampParser;

import com.meridiantrust.sentinel.reference.service.FxConversionService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Stage 3 — is this row plausible as banking data?
 *
 * <p>Two checks that matter more than they look:
 * <ul>
 *   <li><b>Known currency.</b> An amount we cannot convert cannot be compared
 *       against a threshold. Accepting it with an assumed 1:1 rate would let a
 *       10,000 USD transfer slip past a 10,000 INR threshold — a detection
 *       gap disguised as leniency.</li>
 *   <li><b>Duplicate id within the batch.</b> Silently accepting it would
 *       double-count the amount in every window rule, inflating structuring
 *       counts and behavioural baselines with data that does not exist.</li>
 * </ul>
 */
@Component
public class BusinessValidator implements TransactionValidator {

    private static final int FUTURE_TOLERANCE_MINUTES = 5;

    private final FxConversionService fxService;

    public BusinessValidator(FxConversionService fxService) {
        this.fxService = fxService;
    }

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public ValidationOutcome validate(RawTransaction raw, IngestionContext context) {
        String currency = raw.currency().trim().toUpperCase();
        if (!fxService.supports(currency)) {
            return ValidationOutcome.reject(ValidationOutcome.BUSINESS,
                    ("unknown currency '%s' — no rate configured in fx_rates, so the amount cannot be "
                            + "normalised for threshold comparison").formatted(currency));
        }

        LocalDateTime timestamp = TimestampParser.parse(raw.txnTimestamp());
        if (timestamp != null
                && timestamp.isAfter(LocalDateTime.now().plusMinutes(FUTURE_TOLERANCE_MINUTES))) {
            return ValidationOutcome.reject(ValidationOutcome.BUSINESS,
                    "txnTimestamp '%s' is in the future".formatted(raw.txnTimestamp()));
        }

        if (!context.markSeen(raw.transactionId().trim())) {
            return ValidationOutcome.reject(ValidationOutcome.BUSINESS,
                    "duplicate transactionId '%s' within this batch".formatted(raw.transactionId()));
        }
        return ValidationOutcome.ok();
    }
}
