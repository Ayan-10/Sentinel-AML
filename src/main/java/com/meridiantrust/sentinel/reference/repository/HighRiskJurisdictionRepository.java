package com.meridiantrust.sentinel.reference.repository;

import com.meridiantrust.sentinel.reference.model.HighRiskJurisdiction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HighRiskJurisdictionRepository extends JpaRepository<HighRiskJurisdiction, String> {
    List<HighRiskJurisdiction> findByActiveTrue();
}
