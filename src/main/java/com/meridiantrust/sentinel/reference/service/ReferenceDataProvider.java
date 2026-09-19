package com.meridiantrust.sentinel.reference.service;

import com.meridiantrust.sentinel.reference.model.*;
import com.meridiantrust.sentinel.reference.repository.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory lookup over the sanctions / high-risk reference lists.
 *
 * <p>Business rule 4 fires regardless of amount, so this lookup runs against
 * <em>every</em> transaction. At 10,000 transactions that is 10,000 lookups —
 * viable in memory, not viable as database round trips. The lists are small
 * (hundreds of entries) and change rarely, which makes a refreshed cache the
 * right shape.
 */
@Component
public class ReferenceDataProvider {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataProvider.class);

    private final HighRiskJurisdictionRepository jurisdictionRepository;
    private final WatchlistRepository watchlistRepository;

    private volatile Map<String, HighRiskJurisdiction> jurisdictions = Map.of();
    private volatile Map<String, WatchlistCounterparty> watchlist = Map.of();

    public ReferenceDataProvider(HighRiskJurisdictionRepository jurisdictionRepository,
                                 WatchlistRepository watchlistRepository) {
        this.jurisdictionRepository = jurisdictionRepository;
        this.watchlistRepository = watchlistRepository;
    }

    @PostConstruct
    @Transactional(readOnly = true)
    public void refresh() {
        Map<String, HighRiskJurisdiction> byCountry = new HashMap<>();
        for (HighRiskJurisdiction j : jurisdictionRepository.findByActiveTrue()) {
            byCountry.put(j.getCountryCode().toUpperCase(), j);
        }
        Map<String, WatchlistCounterparty> byName = new HashMap<>();
        for (WatchlistCounterparty w : watchlistRepository.findByActiveTrue()) {
            byName.put(w.getNormalizedName(), w);
        }
        this.jurisdictions = Map.copyOf(byCountry);
        this.watchlist = Map.copyOf(byName);
        log.info("Reference data refreshed: {} jurisdictions, {} watchlisted counterparties",
                jurisdictions.size(), watchlist.size());
    }

    public Optional<JurisdictionMatch> matchCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return Optional.empty();
        }
        HighRiskJurisdiction j = jurisdictions.get(countryCode.trim().toUpperCase());
        return j == null ? Optional.empty() : Optional.of(new JurisdictionMatch(
                JurisdictionMatch.TYPE_COUNTRY, j.getCountryName(), j.getCategory(),
                j.getRiskWeight(), j.getSource()));
    }

    /** Matches on the normalised form, so punctuation and casing cannot hide a designated party. */
    public Optional<JurisdictionMatch> matchCounterparty(String counterpartyName) {
        if (counterpartyName == null || counterpartyName.isBlank()) {
            return Optional.empty();
        }
        WatchlistCounterparty w = watchlist.get(WatchlistCounterparty.normalize(counterpartyName));
        return w == null ? Optional.empty() : Optional.of(new JurisdictionMatch(
                JurisdictionMatch.TYPE_COUNTERPARTY, w.getName(), w.getListType(),
                riskWeightFor(w.getListType()), w.getNotes()));
    }

    /** Country-level risk uplift, 0 when the country is not listed. */
    public int countryRiskWeight(String countryCode) {
        return matchCountry(countryCode).map(JurisdictionMatch::riskWeight).orElse(0);
    }

    private int riskWeightFor(String listType) {
        return switch (listType == null ? "" : listType.toUpperCase()) {
            case "SANCTIONS" -> 40;
            case "SHELL_COMPANY" -> 30;
            case "ADVERSE_MEDIA" -> 20;
            default -> 15;
        };
    }
}
