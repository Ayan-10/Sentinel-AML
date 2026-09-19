package com.meridiantrust.sentinel.common.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable monetary value object — an amount bound to its currency.
 *
 * <p>Business rule 9 requires every amount to be comparable across currencies.
 * Making money a type rather than a loose {@code BigDecimal} means the compiler
 * stops us from accidentally comparing 100 USD with 100 INR: arithmetic between
 * mismatched currencies throws rather than silently producing nonsense.
 *
 * <p>All instances are normalised to 2 decimal places with HALF_UP rounding,
 * the convention used throughout retail banking settlement.
 */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {

    public static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code: " + currency);
        }
        currency = currency.toUpperCase();
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money of(double amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), currency);
    }

    /** Ratio of this amount to {@code other}, e.g. outflow / deposit for BR3. */
    public BigDecimal ratioTo(Money other) {
        requireSameCurrency(other);
        if (other.amount.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return this.amount.divide(other.amount, 6, RoundingMode.HALF_UP);
    }

    public boolean isGreaterThanOrEqual(BigDecimal threshold) {
        return amount.compareTo(threshold) >= 0;
    }

    public boolean isBetweenInclusive(BigDecimal lower, BigDecimal upper) {
        return amount.compareTo(lower) >= 0 && amount.compareTo(upper) <= 0;
    }

    /** True when the amount is an exact multiple of {@code unit} (round-number typology). */
    public boolean isExactMultipleOf(BigDecimal unit) {
        if (unit == null || unit.signum() == 0) {
            return false;
        }
        return amount.remainder(unit).compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: cannot combine %s with %s".formatted(currency, other.currency));
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }
}
