package com.meridiantrust.sentinel.reference.repository;

import com.meridiantrust.sentinel.reference.model.WatchlistCounterparty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<WatchlistCounterparty, Long> {
    List<WatchlistCounterparty> findByActiveTrue();
}
