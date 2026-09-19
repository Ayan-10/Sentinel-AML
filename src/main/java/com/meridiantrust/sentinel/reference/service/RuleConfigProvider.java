package com.meridiantrust.sentinel.reference.service;

import com.meridiantrust.sentinel.reference.model.RuleParams;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridiantrust.sentinel.reference.model.RuleConfig;
import com.meridiantrust.sentinel.reference.repository.RuleConfigRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache-aside access to {@link RuleConfig}, refreshed on write.
 *
 * <p>Detection evaluates every rule against every transaction. Reading
 * configuration from the database on each of those evaluations would add a
 * query per rule per transaction and make the 10,000-in-two-minutes target
 * unreachable. Caching it makes detection fast; invalidating on write keeps the
 * "tunable without redeployment" guarantee — a {@code PATCH} takes effect on
 * the very next evaluation, with no restart.
 *
 * <p>NFR (Concurrency): the cache is a {@link ConcurrentHashMap} replaced
 * wholesale by reference on refresh, so readers always observe a complete,
 * internally consistent snapshot rather than a half-updated map.
 */
@Component
public class RuleConfigProvider {

    private static final Logger log = LoggerFactory.getLogger(RuleConfigProvider.class);
    private static final TypeReference<Map<String, Object>> PARAM_TYPE = new TypeReference<>() {};

    private final RuleConfigRepository repository;
    private final ObjectMapper objectMapper;

    private volatile Map<String, RuleConfig> configCache = new ConcurrentHashMap<>();
    private volatile Map<String, RuleParams> paramCache = new ConcurrentHashMap<>();

    public RuleConfigProvider(RuleConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    @Transactional(readOnly = true)
    public void refresh() {
        Map<String, RuleConfig> configs = new ConcurrentHashMap<>();
        Map<String, RuleParams> params = new ConcurrentHashMap<>();
        for (RuleConfig config : repository.findAll()) {
            configs.put(config.getRuleCode(), config);
            params.put(config.getRuleCode(), parseParams(config));
        }
        this.configCache = configs;
        this.paramCache = params;
        log.info("Rule configuration cache refreshed: {} rule(s), {} enabled",
                configs.size(), configs.values().stream().filter(RuleConfig::isEnabled).count());
    }

    public boolean isEnabled(String ruleCode) {
        RuleConfig config = configCache.get(ruleCode);
        // Fail closed: a rule with no configuration row does not run. An
        // unconfigured rule firing with implicit defaults would produce alerts
        // nobody can explain or tune.
        return config != null && config.isEnabled();
    }

    public RuleConfig require(String ruleCode) {
        RuleConfig config = configCache.get(ruleCode);
        if (config == null) {
            throw new IllegalStateException("No rule_config row for rule: " + ruleCode);
        }
        return config;
    }

    public RuleParams paramsFor(String ruleCode) {
        return paramCache.getOrDefault(ruleCode, RuleParams.empty());
    }

    public int weightOf(String ruleCode) {
        RuleConfig config = configCache.get(ruleCode);
        return config == null ? 0 : config.getWeight();
    }

    public List<RuleConfig> all() {
        return List.copyOf(configCache.values());
    }

    private RuleParams parseParams(RuleConfig config) {
        String raw = config.getParams();
        if (raw == null || raw.isBlank()) {
            return RuleParams.empty();
        }
        try {
            return new RuleParams(Collections.unmodifiableMap(objectMapper.readValue(raw, PARAM_TYPE)));
        } catch (Exception ex) {
            // Do not let one malformed blob take down the engine — log loudly
            // and fall back to the rule's coded defaults.
            log.error("Malformed params JSON for rule {} — falling back to defaults: {}",
                    config.getRuleCode(), ex.getMessage());
            return RuleParams.empty();
        }
    }
}
