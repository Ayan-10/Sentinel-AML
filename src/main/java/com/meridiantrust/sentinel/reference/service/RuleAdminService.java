package com.meridiantrust.sentinel.reference.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridiantrust.sentinel.common.audit.model.AuditAction;
import com.meridiantrust.sentinel.common.audit.service.AuditService;
import com.meridiantrust.sentinel.common.model.Severity;
import com.meridiantrust.sentinel.common.error.ApiException;
import com.meridiantrust.sentinel.common.security.CurrentUser;
import com.meridiantrust.sentinel.common.security.Roles;
import com.meridiantrust.sentinel.reference.model.*;
import com.meridiantrust.sentinel.reference.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Runtime rule tuning — the "configurable without code redeployment"
 * requirement.
 *
 * <p>Three things happen on every write, and all three matter:
 * <ol>
 *   <li><b>Validation.</b> Parameters are checked before they are accepted. A
 *       typo that silently disabled a control would be worse than a rejected
 *       request, because nobody would notice until an audit.</li>
 *   <li><b>Cache invalidation.</b> The change takes effect on the next
 *       evaluation, with no restart.</li>
 *   <li><b>Audit.</b> Changing a detection threshold is a compliance-significant
 *       act. Who widened the structuring window, and when, is exactly the
 *       question a regulator asks.</li>
 * </ol>
 */
@Service
public class RuleAdminService {

    private static final Logger log = LoggerFactory.getLogger(RuleAdminService.class);

    private final RuleConfigRepository ruleRepository;
    private final FxRateRepository fxRateRepository;
    private final HighRiskJurisdictionRepository jurisdictionRepository;
    private final WatchlistRepository watchlistRepository;
    private final RuleConfigProvider ruleConfigProvider;
    private final ReferenceDataProvider referenceDataProvider;
    private final FxConversionService fxService;
    private final AuditService auditService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public RuleAdminService(RuleConfigRepository ruleRepository,
                            FxRateRepository fxRateRepository,
                            HighRiskJurisdictionRepository jurisdictionRepository,
                            WatchlistRepository watchlistRepository,
                            RuleConfigProvider ruleConfigProvider,
                            ReferenceDataProvider referenceDataProvider,
                            FxConversionService fxService,
                            AuditService auditService,
                            CurrentUser currentUser,
                            ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.fxRateRepository = fxRateRepository;
        this.jurisdictionRepository = jurisdictionRepository;
        this.watchlistRepository = watchlistRepository;
        this.ruleConfigProvider = ruleConfigProvider;
        this.referenceDataProvider = referenceDataProvider;
        this.fxService = fxService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RuleConfig> allRules() {
        return ruleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public RuleConfig requireRule(String ruleCode) {
        return ruleRepository.findById(ruleCode)
                .orElseThrow(() -> new ApiException.NotFound("RuleConfig", ruleCode));
    }

    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public RuleConfig updateRule(String ruleCode, Boolean enabled, Integer weight,
                                 Severity severity, Map<String, Object> params) {
        RuleConfig config = requireRule(ruleCode);
        String before = describe(config);

        if (enabled != null) {
            config.setEnabled(enabled);
        }
        if (weight != null) {
            if (weight < 0 || weight > 100) {
                throw new ApiException.Validation("weight must be between 0 and 100");
            }
            config.setWeight(weight);
        }
        if (severity != null) {
            config.setSeverity(severity);
        }
        if (params != null && !params.isEmpty()) {
            validateParams(ruleCode, params);
            try {
                config.setParams(objectMapper.writeValueAsString(params));
            } catch (Exception ex) {
                throw new ApiException.Validation("params could not be serialised: " + ex.getMessage());
            }
        }

        config.setUpdatedBy(currentUser.username());
        config.setUpdatedAt(Instant.now());
        RuleConfig saved = ruleRepository.save(config);

        // Take effect immediately — no restart, no redeployment.
        ruleConfigProvider.refresh();

        // from_state/to_state describe a lifecycle transition; a configuration
        // change has none, so the before/after snapshot goes in `details`.
        auditService.record(AuditAction.ENTITY_RULE, ruleCode, AuditAction.RULE_UPDATED,
                "Changed by %s | before: %s | after: %s"
                        .formatted(currentUser.username(), before, describe(saved)));
        log.info("Rule {} updated by {}: {} -> {}", ruleCode, currentUser.username(), before, describe(saved));
        return saved;
    }

    /**
     * Guards against configurations that are syntactically fine but would
     * silently break detection — an inverted structuring band matches nothing,
     * a zero window never accumulates, a ratio above 1.0 can never be reached.
     * Each of these would leave a rule that looks enabled but cannot fire.
     */
    private void validateParams(String ruleCode, Map<String, Object> params) {
        RuleParams candidate = new RuleParams(params);

        if (params.containsKey("windowHours") && candidate.getInt("windowHours", 0) <= 0) {
            throw new ApiException.Validation("windowHours must be greater than zero");
        }
        if (params.containsKey("minCount") && candidate.getInt("minCount", 0) < 1) {
            throw new ApiException.Validation("minCount must be at least 1");
        }
        if (params.containsKey("lowerBound") || params.containsKey("upperBound")) {
            BigDecimal lower = candidate.getDecimal("lowerBound", BigDecimal.ZERO);
            BigDecimal upper = candidate.getDecimal("upperBound", BigDecimal.ZERO);
            if (lower.compareTo(upper) >= 0) {
                throw new ApiException.Validation(
                        "lowerBound (%s) must be less than upperBound (%s) — an inverted band matches nothing"
                                .formatted(lower, upper));
            }
        }
        if (params.containsKey("outflowRatio")) {
            BigDecimal ratio = candidate.getDecimal("outflowRatio", BigDecimal.ZERO);
            if (ratio.signum() <= 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
                throw new ApiException.Validation(
                        "outflowRatio must be between 0 (exclusive) and 1.0 (inclusive)");
            }
        }
        if (params.containsKey("multiplier")
                && candidate.getDecimal("multiplier", BigDecimal.ZERO).signum() <= 0) {
            throw new ApiException.Validation("multiplier must be greater than zero");
        }
        if (params.containsKey("baselineDays") && candidate.getInt("baselineDays", 0) < 1) {
            throw new ApiException.Validation("baselineDays must be at least 1");
        }
        log.debug("Validated params for rule {}: {}", ruleCode, params);
    }

    // --- Reference lists ---------------------------------------------------

    @Transactional(readOnly = true)
    public List<HighRiskJurisdiction> jurisdictions() {
        return jurisdictionRepository.findAll();
    }

    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public HighRiskJurisdiction upsertJurisdiction(HighRiskJurisdiction jurisdiction) {
        jurisdiction.setCountryCode(jurisdiction.getCountryCode().toUpperCase());
        HighRiskJurisdiction saved = jurisdictionRepository.save(jurisdiction);
        referenceDataProvider.refresh();
        auditService.record(AuditAction.ENTITY_REFERENCE, saved.getCountryCode(),
                AuditAction.REFERENCE_DATA_UPDATED,
                "Jurisdiction %s set to %s (weight %d)".formatted(
                        saved.getCountryCode(), saved.getCategory(), saved.getRiskWeight()));
        return saved;
    }

    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public void deactivateJurisdiction(String countryCode) {
        HighRiskJurisdiction j = jurisdictionRepository.findById(countryCode.toUpperCase())
                .orElseThrow(() -> new ApiException.NotFound("Jurisdiction", countryCode));
        // Deactivated, never deleted — the historical alerts that cite this
        // listing must remain explicable.
        j.setActive(false);
        jurisdictionRepository.save(j);
        referenceDataProvider.refresh();
        auditService.record(AuditAction.ENTITY_REFERENCE, countryCode,
                AuditAction.REFERENCE_DATA_UPDATED, "Jurisdiction deactivated");
    }

    @Transactional(readOnly = true)
    public List<WatchlistCounterparty> watchlist() {
        return watchlistRepository.findAll();
    }

    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public WatchlistCounterparty addWatchlistEntry(WatchlistCounterparty entry) {
        entry.setNormalizedName(WatchlistCounterparty.normalize(entry.getName()));
        WatchlistCounterparty saved = watchlistRepository.save(entry);
        referenceDataProvider.refresh();
        auditService.record(AuditAction.ENTITY_REFERENCE, saved.getName(),
                AuditAction.REFERENCE_DATA_UPDATED, "Watchlist entry added: " + saved.getListType());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FxRate> fxRates() {
        return fxRateRepository.findAll();
    }

    @Transactional
    @PreAuthorize(Roles.HAS_ADMIN)
    public FxRate updateFxRate(String currency, BigDecimal rateToBase) {
        FxRate saved = fxService.upsert(currency, rateToBase);
        auditService.record(AuditAction.ENTITY_REFERENCE, currency,
                AuditAction.REFERENCE_DATA_UPDATED,
                "FX rate set to %s per base unit".formatted(rateToBase));
        return saved;
    }

    private String describe(RuleConfig config) {
        return "enabled=%s,weight=%d,severity=%s,params=%s".formatted(
                config.isEnabled(), config.getWeight(), config.getSeverity(), config.getParams());
    }
}
