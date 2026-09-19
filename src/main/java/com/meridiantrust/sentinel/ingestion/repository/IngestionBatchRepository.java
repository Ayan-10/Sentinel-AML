package com.meridiantrust.sentinel.ingestion.repository;

import com.meridiantrust.sentinel.ingestion.model.IngestionBatch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngestionBatchRepository extends JpaRepository<IngestionBatch, Long> {
    Optional<IngestionBatch> findByBatchRef(String batchRef);
    Page<IngestionBatch> findAllByOrderByStartedAtDesc(Pageable pageable);
}
