package com.meridiantrust.sentinel.customer.repository;

import com.meridiantrust.sentinel.customer.model.Customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    /** Bulk fetch for detection — avoids an N+1 lookup per transaction. */
    @Query("select c from Customer c where c.customerId in :ids")
    List<Customer> findAllByIds(Collection<String> ids);

    Page<Customer> findByRiskRatingIn(Collection<com.meridiantrust.sentinel.common.model.RiskRating> ratings,
                                      Pageable pageable);

    boolean existsByCustomerId(String customerId);
}
