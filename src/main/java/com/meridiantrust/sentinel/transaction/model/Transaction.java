package com.meridiantrust.sentinel.transaction.model;

import com.meridiantrust.sentinel.common.model.Money;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A single monetary movement — the unit the detection engine evaluates.
 *
 * <p>Business rule 9: {@code amountBase} is computed once, at ingestion, using
 * {@code fxRateApplied}. Both are persisted so an alert can be re-derived
 * exactly as it was scored, even after exchange rates move. Detection rules
 * read {@link #baseAmount()} and never the raw {@code amount}, which is what
 * makes cross-currency comparison correct by construction.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @Column(name = "transaction_id", length = 40)
    private String transactionId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    /**
     * Denormalised from {@code accounts} — derivable, but business rule 5 and
     * the alert queue are customer-scoped, and this removes a join from the
     * hottest detection path. Referential integrity is still enforced by FK.
     */
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "txn_timestamp", nullable = false)
    private LocalDateTime txnTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Direction direction;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "amount_base", nullable = false)
    private BigDecimal amountBase;

    @Column(name = "fx_rate_applied", nullable = false)
    private BigDecimal fxRateApplied = BigDecimal.ONE;

    private String channel;

    @Column(name = "txn_type")
    private String txnType;

    @Column(name = "counterparty_name")
    private String counterpartyName;

    @Column(name = "counterparty_account")
    private String counterpartyAccount;

    @Column(name = "counterparty_bank")
    private String counterpartyBank;

    @Column(name = "counterparty_country", length = 2)
    private String counterpartyCountry;

    private String description;

    @Column(nullable = false)
    private String status = "POSTED";

    @Column(name = "ingested_at", insertable = false, updatable = false)
    private Instant ingestedAt;

    /** The normalised amount as a typed value — what every rule evaluates. */
    @Transient
    public Money baseAmount() {
        return Money.of(amountBase, "INR");
    }

    @Transient
    public boolean isCredit() {
        return direction == Direction.CREDIT;
    }

    @Transient
    public boolean isDebit() {
        return direction == Direction.DEBIT;
    }
}
