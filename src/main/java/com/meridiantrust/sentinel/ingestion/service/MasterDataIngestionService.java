package com.meridiantrust.sentinel.ingestion.service;

import com.meridiantrust.sentinel.ingestion.validation.ValidationOutcome;

import com.meridiantrust.sentinel.account.model.Account;
import com.meridiantrust.sentinel.account.repository.AccountRepository;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.customer.repository.CustomerRepository;
import com.meridiantrust.sentinel.ingestion.model.*;
import com.meridiantrust.sentinel.ingestion.repository.*;
import com.meridiantrust.sentinel.ingestion.mapper.CsvRowMappers;
import com.meridiantrust.sentinel.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Customer and account (KYC / master data) ingestion.
 *
 * <p>Separated from {@link IngestionService} on Single Responsibility grounds:
 * master data is reference material loaded occasionally and never triggers
 * detection, whereas transactions are a continuous flow that always does. They
 * share a batch-reporting shape but nothing else, and conflating them would
 * mean one class with two reasons to change.
 */
@Service
public class MasterDataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MasterDataIngestionService.class);
    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final IngestionBatchRepository batchRepository;
    private final IngestionErrorRepository errorRepository;
    private final CurrentUser currentUser;

    public MasterDataIngestionService(CustomerRepository customerRepository,
                                      AccountRepository accountRepository,
                                      IngestionBatchRepository batchRepository,
                                      IngestionErrorRepository errorRepository,
                                      CurrentUser currentUser) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.batchRepository = batchRepository;
        this.errorRepository = errorRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public IngestionResult ingestCustomers(List<Map<String, String>> rows, String source) {
        long start = System.currentTimeMillis();
        IngestionBatch batch = startBatch("CUSTOMER", source, rows.size());

        List<Customer> accepted = new ArrayList<>();
        List<IngestionError> errors = new ArrayList<>();
        List<IngestionResult.RejectedRecord> summaries = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        int rowNumber = 0;
        for (Map<String, String> row : rows) {
            rowNumber++;
            String id = row.get("customer_id");
            if (id == null || id.isBlank()) {
                reject(batch, errors, summaries, rowNumber, null,
                        ValidationOutcome.STRUCTURAL, "customer_id is required", row.toString());
                continue;
            }
            if (!seen.add(id)) {
                reject(batch, errors, summaries, rowNumber, id,
                        ValidationOutcome.BUSINESS, "duplicate customer_id in file", row.toString());
                continue;
            }
            try {
                accepted.add(CsvRowMappers.toCustomer(row));
            } catch (Exception ex) {
                reject(batch, errors, summaries, rowNumber, id,
                        ValidationOutcome.STRUCTURAL, ex.getMessage(), row.toString());
            }
        }

        customerRepository.saveAll(accepted);
        errorRepository.saveAll(errors);
        long duration = finish(batch, accepted.size(), errors.size(), start);
        log.info("Customer ingestion {}: {} accepted, {} rejected", batch.getBatchRef(),
                accepted.size(), errors.size());

        return new IngestionResult(batch.getBatchRef(), "CUSTOMER", rows.size(),
                accepted.size(), errors.size(), 0, duration, summaries);
    }

    @Transactional
    public IngestionResult ingestAccounts(List<Map<String, String>> rows, String source) {
        long start = System.currentTimeMillis();
        IngestionBatch batch = startBatch("ACCOUNT", source, rows.size());

        List<Account> accepted = new ArrayList<>();
        List<IngestionError> errors = new ArrayList<>();
        List<IngestionResult.RejectedRecord> summaries = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        int rowNumber = 0;
        for (Map<String, String> row : rows) {
            rowNumber++;
            String id = row.get("account_id");
            String customerId = row.get("customer_id");

            if (id == null || id.isBlank()) {
                reject(batch, errors, summaries, rowNumber, null,
                        ValidationOutcome.STRUCTURAL, "account_id is required", row.toString());
                continue;
            }
            if (!seen.add(id)) {
                reject(batch, errors, summaries, rowNumber, id,
                        ValidationOutcome.BUSINESS, "duplicate account_id in file", row.toString());
                continue;
            }
            // Referential integrity: an account whose owner does not exist would
            // orphan every transaction booked against it.
            if (customerId == null || !customerRepository.existsById(customerId)) {
                reject(batch, errors, summaries, rowNumber, id, ValidationOutcome.REFERENTIAL,
                        "customer_id '%s' does not exist — load customers first".formatted(customerId),
                        row.toString());
                continue;
            }
            try {
                accepted.add(CsvRowMappers.toAccount(row));
            } catch (Exception ex) {
                reject(batch, errors, summaries, rowNumber, id,
                        ValidationOutcome.STRUCTURAL, ex.getMessage(), row.toString());
            }
        }

        accountRepository.saveAll(accepted);
        errorRepository.saveAll(errors);
        long duration = finish(batch, accepted.size(), errors.size(), start);
        log.info("Account ingestion {}: {} accepted, {} rejected", batch.getBatchRef(),
                accepted.size(), errors.size());

        return new IngestionResult(batch.getBatchRef(), "ACCOUNT", rows.size(),
                accepted.size(), errors.size(), 0, duration, summaries);
    }

    private void reject(IngestionBatch batch, List<IngestionError> errors,
                        List<IngestionResult.RejectedRecord> summaries,
                        int rowNumber, String key, String type, String message, String raw) {
        errors.add(new IngestionError(batch.getId(), rowNumber, key, type, message, raw));
        summaries.add(new IngestionResult.RejectedRecord(rowNumber, key, type, message));
    }

    private IngestionBatch startBatch(String entityType, String source, int total) {
        IngestionBatch batch = new IngestionBatch();
        batch.setBatchRef("BATCH-%s-%s".formatted(LocalDateTime.now().format(REF_DATE),
                UUID.randomUUID().toString().substring(0, 4).toUpperCase()));
        batch.setEntityType(entityType);
        batch.setSource(source);
        batch.setTotalRecords(total);
        batch.setInitiatedBy(currentUser.username());
        return batchRepository.saveAndFlush(batch);
    }

    private long finish(IngestionBatch batch, int accepted, int rejected, long start) {
        long duration = System.currentTimeMillis() - start;
        batch.setAcceptedRecords(accepted);
        batch.setRejectedRecords(rejected);
        batch.setStatus(rejected == 0 ? BatchStatus.COMPLETED : BatchStatus.COMPLETED_WITH_ERRORS);
        batch.setFinishedAt(Instant.now());
        batch.setDurationMs(duration);
        batchRepository.save(batch);
        return duration;
    }
}
