package com.meridiantrust.sentinel.reference.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Business rule 9: the configurable exchange-rate table. A table rather than a
 * constant, because rates change and compliance must be able to correct one
 * without a deployment.
 */
@Entity
@Table(name = "fx_rates")
@Getter
@Setter
@NoArgsConstructor
public class FxRate {

    @Id
    @Column(length = 3)
    private String currency;

    @Column(name = "rate_to_base", nullable = false)
    private BigDecimal rateToBase;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public FxRate(String currency, BigDecimal rateToBase) {
        this.currency = currency.toUpperCase();
        this.rateToBase = rateToBase;
        this.updatedAt = Instant.now();
    }
}
