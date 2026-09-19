package com.meridiantrust.sentinel.account.repository;

import com.meridiantrust.sentinel.account.model.Account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findByCustomerId(String customerId);

    long countByCustomerId(String customerId);
}
