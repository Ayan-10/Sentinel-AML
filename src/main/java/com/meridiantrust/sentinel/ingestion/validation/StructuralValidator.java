package com.meridiantrust.sentinel.ingestion.validation;

import com.meridiantrust.sentinel.ingestion.model.IngestionContext;
import com.meridiantrust.sentinel.ingestion.model.RawTransaction;
import com.meridiantrust.sentinel.ingestion.util.TimestampParser;

import com.meridiantrust.sentinel.transaction.model.Direction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stage 1 — is this row well-formed?
 *
 * <p>Runs first because it is the cheapest and because later stages assume
 * parseable values.
 */
@Component
public class StructuralValidator implements TransactionValidator {

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public ValidationOutcome validate(RawTransaction raw, IngestionContext context) {
        if (isBlank(raw.transactionId())) {
            return ValidationOutcome.reject(ValidationOutcome.STRUCTURAL, "transactionId is required");
        }
        if (isBlank(raw.accountId())) {
            return ValidationOutcome.reject(ValidationOutcome.STRUCTURAL, "accountId is required");
        }
        if (isBlank(raw.txnTimestamp())) {
            return ValidationOutcome.reject(ValidationOutcome.STRUCTURAL, "txnTimestamp is required");
        }
        if (TimestampParser.parse(raw.txnTimestamp()) == null) {
            return ValidationOutcome.reject(ValidationOutcome.STRUCTURAL,
                    "txnTimestamp '%s' is not a recognised date-time".formatted(raw.txnTimestamp()));
        }
        try {
            Direction.parse(raw.direction());
        } catch (IllegalArgumentException ex) {
            return ValidationOutcome.reject(ValidationOutcome.STRUCTURAL, ex.getMessage());
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(raw.amount().trim());
        } catch (Exception ex) {
            return ValidationOutcome.reject(ValidationOutcome.STRUCTURAL,
                    "amount '%s' is not a valid number".formatted(raw.amount()));
        }
        // A zero or negative amount is not a direction error — direction is a
        // separate field — so it is always a malformed record.
        if (amount.signum() <= 0) {
            return ValidationOutcome.reject(ValidationOutcome.STRUCTURAL,
                    "amount must be greater than zero (was %s)".formatted(amount.toPlainString()));
        }
        if (isBlank(raw.currency()) || raw.currency().trim().length() != 3) {
            return ValidationOutcome.reject(ValidationOutcome.STRUCTURAL,
                    "currency must be a 3-letter ISO-4217 code (was '%s')".formatted(raw.currency()));
        }
        String country = raw.counterpartyCountry();
        if (country != null && !country.isBlank() && country.trim().length() != 2) {
            return ValidationOutcome.reject(ValidationOutcome.STRUCTURAL,
                    "counterpartyCountry must be a 2-letter ISO code (was '%s')".formatted(country));
        }
        return ValidationOutcome.ok();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
