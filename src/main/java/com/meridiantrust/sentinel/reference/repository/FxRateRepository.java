package com.meridiantrust.sentinel.reference.repository;

import com.meridiantrust.sentinel.reference.model.FxRate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, String> {
}
