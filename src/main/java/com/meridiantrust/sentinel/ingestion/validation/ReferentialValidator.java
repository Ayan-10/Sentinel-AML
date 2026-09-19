package com.meridiantrust.sentinel.ingestion.validation;

import com.meridiantrust.sentinel.ingestion.model.IngestionContext;
import com.meridiantrust.sentinel.ingestion.model.RawTransaction;

import com.meridiantrust.sentinel.account.model.Account;
import org.springframework.stereotype.Component;

/**
 * Stage 2 — does this row point at real, consistent entities?
 *
 * <p>The brief requires referential integrity to be enforced, and the subtle
 * half of that is the <em>consistency</em> check, not merely existence: a row
 * naming a valid account and a valid customer who does not own it would pass a
 * pair of foreign keys and still corrupt every customer-scoped rule. Business
 * rule 5's baseline would be computed over another customer's activity.
 *
 * <p>Where the customer is omitted we derive it from the account rather than
 * rejecting — the account is the authoritative link, so a missing customer id
 * is a gap to fill, not an error.
 */
@Component
public class ReferentialValidator implements TransactionValidator {

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public ValidationOutcome validate(RawTransaction raw, IngestionContext context) {
        Account account = context.account(raw.accountId());
        if (account == null) {
            return ValidationOutcome.reject(ValidationOutcome.REFERENTIAL,
                    "accountId '%s' does not exist".formatted(raw.accountId()));
        }
        String customerId = raw.customerId();
        if (customerId != null && !customerId.isBlank()
                && !customerId.trim().equals(account.getCustomerId())) {
            return ValidationOutcome.reject(ValidationOutcome.REFERENTIAL,
                    "customerId '%s' does not own account '%s' (owner is '%s')"
                            .formatted(customerId, raw.accountId(), account.getCustomerId()));
        }
        return ValidationOutcome.ok();
    }
}
