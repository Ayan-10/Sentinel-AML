package com.meridiantrust.sentinel.reference.service;

import com.meridiantrust.sentinel.reference.model.CurrencyConversion;

import com.meridiantrust.sentinel.common.config.SentinelProperties;
import com.meridiantrust.sentinel.common.model.Money;
import com.meridiantrust.sentinel.common.error.ApiException;
import com.meridiantrust.sentinel.reference.model.FxRate;
import com.meridiantrust.sentinel.reference.repository.FxRateRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business rule 9: normalises every monetary amount to the configured base
 * currency so transactions in different currencies become comparable.
 *
 * <p>Conversion happens once, at ingestion (see
 * {@code docs/ARCHITECTURE.md} ADR-005). Converting at query time instead would
 * push an FX join into every detection query and — worse — would silently
 * re-price historical alerts whenever a rate was corrected, so an alert's
 * evidence would stop matching the score it was given.
 */
@Service
public class FxConversionService {

    private static final Logger log = LoggerFactory.getLogger(FxConversionService.class);

    private final FxRateRepository repository;
    private final String baseCurrency;

    private volatile Map<String, BigDecimal> rateCache = new ConcurrentHashMap<>();

    public FxConversionService(FxRateRepository repository, SentinelProperties properties) {
        this.repository = repository;
        this.baseCurrency = properties.baseCurrency();
    }

    @PostConstruct
    @Transactional(readOnly = true)
    public void refresh() {
        Map<String, BigDecimal> rates = new ConcurrentHashMap<>();
        for (FxRate rate : repository.findAll()) {
            rates.put(rate.getCurrency().toUpperCase(), rate.getRateToBase());
        }
        rates.putIfAbsent(baseCurrency, BigDecimal.ONE);
        this.rateCache = rates;
        log.info("FX rate cache refreshed: {} currencies, base={}", rates.size(), baseCurrency);
    }

    public String baseCurrency() {
        return baseCurrency;
    }

    public boolean supports(String currency) {
        return currency != null && rateCache.containsKey(currency.toUpperCase());
    }

    /**
     * Converts to base currency.
     *
     * @throws ApiException.Validation when the currency has no configured rate.
     *         Rejecting is deliberate — assuming 1:1 for an unknown currency
     *         would let a 10,000 USD transfer slip past a 10,000 INR threshold.
     */
    public CurrencyConversion toBase(Money money) {
        String currency = money.currency();
        BigDecimal rate = rateCache.get(currency);
        if (rate == null) {
            throw new ApiException.Validation(
                    "unknown currency '%s' — no rate configured in fx_rates".formatted(currency));
        }
        Money base = Money.of(money.amount().multiply(rate), baseCurrency);
        return new CurrencyConversion(money, base, rate);
    }

    @Transactional
    public FxRate upsert(String currency, BigDecimal rateToBase) {
        if (rateToBase == null || rateToBase.signum() <= 0) {
            throw new ApiException.Validation("rateToBase must be greater than zero");
        }
        FxRate rate = repository.findById(currency.toUpperCase())
                .orElseGet(() -> new FxRate(currency, rateToBase));
        rate.setRateToBase(rateToBase);
        rate.setUpdatedAt(java.time.Instant.now());
        FxRate saved = repository.save(rate);
        refresh();
        return saved;
    }
}
