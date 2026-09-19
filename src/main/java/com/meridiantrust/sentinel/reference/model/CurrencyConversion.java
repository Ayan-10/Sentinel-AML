package com.meridiantrust.sentinel.reference.model;

import com.meridiantrust.sentinel.common.model.Money;

import java.math.BigDecimal;

/**
 * The outcome of normalising an amount to the base currency.
 *
 * <p>Carries the rate that was applied, not just the result. Persisting the
 * rate alongside the converted amount is what makes an alert reproducible: a
 * reviewer can re-derive the figure exactly as the engine saw it, months later,
 * after the rate table has moved on.
 */
public record CurrencyConversion(Money original, Money base, BigDecimal rateApplied) {
}
