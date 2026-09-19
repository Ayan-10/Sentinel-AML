package com.meridiantrust.sentinel.reference.repository;

import com.meridiantrust.sentinel.reference.model.RuleConfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleConfigRepository extends JpaRepository<RuleConfig, String> {
    List<RuleConfig> findByEnabledTrue();
}
