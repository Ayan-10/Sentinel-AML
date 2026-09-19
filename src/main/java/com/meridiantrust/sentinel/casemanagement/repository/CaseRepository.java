package com.meridiantrust.sentinel.casemanagement.repository;

import com.meridiantrust.sentinel.casemanagement.model.CaseFile;
import com.meridiantrust.sentinel.casemanagement.model.CaseStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseFile, Long> {

    Optional<CaseFile> findByCaseRef(String caseRef);

    @Query("""
           select c from CaseFile c
           where (:status is null or c.status = :status)
             and (:assignedTo is null or c.assignedTo = :assignedTo)
             and (:customerId is null or c.customerId = :customerId)
           """)
    Page<CaseFile> search(@Param("status") CaseStatus status,
                          @Param("assignedTo") String assignedTo,
                          @Param("customerId") String customerId,
                          Pageable pageable);

    long countByStatus(CaseStatus status);
}
