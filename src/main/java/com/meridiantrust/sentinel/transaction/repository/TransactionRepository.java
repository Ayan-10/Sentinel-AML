package com.meridiantrust.sentinel.transaction.repository;

import com.meridiantrust.sentinel.transaction.model.DailyVolume;
import com.meridiantrust.sentinel.transaction.model.Transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * NFR (Performance): the bulk path must evaluate 10,000 transactions in under
 * two minutes. The naive shape — one window query per transaction — is O(n)
 * round trips and misses that budget. These batched, {@code IN}-clause queries
 * let {@code RuleWindowLoader} fetch every window a whole chunk needs in a
 * fixed number of statements, turning O(n) queries into O(chunks).
 */
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** All transactions for a set of accounts inside a time window — account-scoped rules. */
    @Query("""
           select t from Transaction t
           where t.accountId in :accountIds
             and t.txnTimestamp >= :from
             and t.txnTimestamp <= :to
           order by t.accountId asc, t.txnTimestamp asc
           """)
    List<Transaction> findWindowByAccounts(@Param("accountIds") Collection<String> accountIds,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * Daily totals per customer over the baseline period. Aggregated in the
     * database so business rule 5 never loads 90 days of rows per customer.
     */
    @Query("""
           select new com.meridiantrust.sentinel.transaction.model.DailyVolume(
                    t.customerId,
                    cast(t.txnTimestamp as LocalDate),
                    sum(t.amountBase),
                    count(t))
           from Transaction t
           where t.customerId in :customerIds
             and t.txnTimestamp >= :from
             and t.txnTimestamp <= :to
           group by t.customerId, cast(t.txnTimestamp as LocalDate)
           order by t.customerId asc, cast(t.txnTimestamp as LocalDate) asc
           """)
    List<DailyVolume> findDailyVolumes(@Param("customerIds") Collection<String> customerIds,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    Page<Transaction> findByCustomerIdOrderByTxnTimestampDesc(String customerId, Pageable pageable);

    List<Transaction> findByTransactionIdIn(Collection<String> ids);

    @Query("select count(t) from Transaction t")
    long countAll();

    boolean existsByTransactionId(String transactionId);
}
