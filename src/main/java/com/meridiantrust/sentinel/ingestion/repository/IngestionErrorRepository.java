package com.meridiantrust.sentinel.ingestion.repository;

import com.meridiantrust.sentinel.ingestion.model.IngestionError;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngestionErrorRepository extends JpaRepository<IngestionError, Long> {
    List<IngestionError> findByBatchIdOrderByRowNumberAsc(Long batchId);
}
