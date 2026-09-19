package com.meridiantrust.sentinel.ingestion.service;

import com.meridiantrust.sentinel.ingestion.port.TransactionIngestPort;
import com.meridiantrust.sentinel.ingestion.util.TimestampParser;
import com.meridiantrust.sentinel.ingestion.validation.TransactionValidator;
import com.meridiantrust.sentinel.ingestion.validation.ValidationOutcome;

import com.meridiantrust.sentinel.account.model.Account;
import com.meridiantrust.sentinel.account.repository.AccountRepository;
import com.meridiantrust.sentinel.common.audit.model.AuditAction;
import com.meridiantrust.sentinel.common.audit.service.AuditService;
import com.meridiantrust.sentinel.common.model.Money;
import com.meridiantrust.sentinel.common.security.CurrentUser;
import com.meridiantrust.sentinel.detection.model.DetectionResult;
import com.meridiantrust.sentinel.detection.service.DetectionService;
import com.meridiantrust.sentinel.ingestion.model.*;
import com.meridiantrust.sentinel.ingestion.repository.*;
import com.meridiantrust.sentinel.reference.model.CurrencyConversion;
import com.meridiantrust.sentinel.reference.service.FxConversionService;
import com.meridiantrust.sentinel.transaction.model.Direction;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import com.meridiantrust.sentinel.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transaction ingestion: validate, normalise, persist, then detect.
 *
 * <p>The ordering matters and is not arbitrary. Validation precedes
 * persistence so bad data never enters the store; normalisation precedes
 * persistence so business rule 9 holds for every row from the moment it lands;
 * detection follows persistence so window-based rules can see the new
 * transactions alongside history in a single query.
 */
@Service
public class IngestionService implements TransactionIngestPort {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int PERSIST_BATCH = 500;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final IngestionBatchRepository batchRepository;
    private final IngestionErrorRepository errorRepository;
    private final List<TransactionValidator> validators;
    private final FxConversionService fxService;
    private final DetectionService detectionService;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public IngestionService(TransactionRepository transactionRepository,
                            AccountRepository accountRepository,
                            IngestionBatchRepository batchRepository,
                            IngestionErrorRepository errorRepository,
                            List<TransactionValidator> validators,
                            FxConversionService fxService,
                            DetectionService detectionService,
                            AuditService auditService,
                            CurrentUser currentUser) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.batchRepository = batchRepository;
        this.errorRepository = errorRepository;
        // Sorted by declared order: cheap structural checks before lookups.
        this.validators = validators.stream()
                .sorted(Comparator.comparingInt(TransactionValidator::getOrder))
                .toList();
        this.fxService = fxService;
        this.detectionService = detectionService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        log.info("Ingestion validation chain: {}",
                this.validators.stream().map(v -> v.getClass().getSimpleName()).toList());
    }

    /**
     * NOT {@code @Transactional} — deliberately, and this is load-bearing.
     *
     * <p>Bulk detection evaluates chunks on a worker pool. If ingestion held an
     * open transaction here, those worker threads would query the database on
     * their own connections and could not see the rows this transaction had not
     * yet committed. Every window-based rule (structuring, rapid movement,
     * behavioural deviation, round amounts) would load an empty history and
     * silently find nothing — the rules would appear to work while detecting
     * nothing at all.
     *
     * <p>So persistence commits first, in its own transactions, and detection
     * then runs against committed data that every worker can read.
     */
    @Override
    public IngestionResult ingest(List<RawTransaction> records, String source, boolean runDetection) {
        long start = System.currentTimeMillis();
        IngestionBatch batch = startBatch("TRANSACTION", source, records.size());

        IngestionContext context = IngestionContext.of(loadReferencedAccounts(records));

        List<Transaction> accepted = new ArrayList<>();
        List<IngestionError> rejections = new ArrayList<>();
        List<IngestionResult.RejectedRecord> rejectionSummaries = new ArrayList<>();

        int rowNumber = 0;
        for (RawTransaction raw : records) {
            rowNumber++;
            ValidationOutcome outcome = runChain(raw, context);
            if (!outcome.valid()) {
                // Per-record rejection: one malformed row must never sink the
                // other 9,999.
                rejections.add(new IngestionError(batch.getId(), rowNumber, raw.transactionId(),
                        outcome.errorType(), outcome.message(), raw.rawLine()));
                rejectionSummaries.add(new IngestionResult.RejectedRecord(
                        rowNumber, raw.transactionId(), outcome.errorType(), outcome.message()));
                continue;
            }
            accepted.add(toEntity(raw, context));
        }

        persistInBatches(accepted);
        errorRepository.saveAll(rejections);

        int alertsCreated = 0;
        if (runDetection && !accepted.isEmpty()) {
            DetectionResult detection = detectionService.runBulk(accepted);
            alertsCreated = detection.alertsCreated();
        }

        long duration = System.currentTimeMillis() - start;
        finishBatch(batch, accepted.size(), rejections.size(), alertsCreated, duration);

        log.info("Ingestion {} complete: {} accepted, {} rejected, {} alerts, {} ms",
                batch.getBatchRef(), accepted.size(), rejections.size(), alertsCreated, duration);

        return new IngestionResult(batch.getBatchRef(), "TRANSACTION", records.size(),
                accepted.size(), rejections.size(), alertsCreated, duration, rejectionSummaries);
    }

    /**
     * Streaming path: a single transaction, evaluated synchronously so the
     * caller learns in the same request whether it alerted (NFR: sub-second
     * streaming latency).
     */
    public DetectionResult ingestStreaming(RawTransaction raw) {
        Map<String, Account> accounts = loadReferencedAccounts(List.of(raw));
        IngestionContext context = IngestionContext.of(accounts);

        ValidationOutcome outcome = runChain(raw, context);
        if (!outcome.valid()) {
            throw new com.meridiantrust.sentinel.common.error.ApiException.Validation(
                    "%s: %s".formatted(outcome.errorType(), outcome.message()));
        }

        // Committed before detection, for the same visibility reason as the bulk path.
        Transaction txn = transactionRepository.saveAndFlush(toEntity(raw, context));
        return detectionService.runStreaming(txn);
    }

    // --- internals ---------------------------------------------------------

    private ValidationOutcome runChain(RawTransaction raw, IngestionContext context) {
        for (TransactionValidator validator : validators) {
            ValidationOutcome outcome = validator.validate(raw, context);
            if (!outcome.valid()) {
                return outcome;   // short-circuit at the first failure
            }
        }
        return ValidationOutcome.ok();
    }

    /** One query for every account the batch references, rather than one per row. */
    private Map<String, Account> loadReferencedAccounts(List<RawTransaction> records) {
        Set<String> accountIds = records.stream()
                .map(RawTransaction::accountId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity(), (a, b) -> a));
    }

    private Transaction toEntity(RawTransaction raw, IngestionContext context) {
        Account account = context.account(raw.accountId());

        Money original = Money.of(new BigDecimal(raw.amount().trim()), raw.currency().trim());
        // Business rule 9: normalise once, here, and keep the rate applied.
        CurrencyConversion conversion = fxService.toBase(original);

        Transaction txn = new Transaction();
        txn.setTransactionId(raw.transactionId().trim());
        txn.setAccountId(account.getAccountId());
        txn.setCustomerId(account.getCustomerId());
        txn.setTxnTimestamp(TimestampParser.parse(raw.txnTimestamp()));
        txn.setDirection(Direction.parse(raw.direction()));
        txn.setAmount(original.amount());
        txn.setCurrency(original.currency());
        txn.setAmountBase(conversion.base().amount());
        txn.setFxRateApplied(conversion.rateApplied());
        txn.setChannel(raw.channel());
        txn.setTxnType(raw.txnType());
        txn.setCounterpartyName(raw.counterpartyName());
        txn.setCounterpartyAccount(raw.counterpartyAccount());
        txn.setCounterpartyBank(raw.counterpartyBank());
        txn.setCounterpartyCountry(raw.counterpartyCountry() == null
                ? null : raw.counterpartyCountry().trim().toUpperCase());
        txn.setDescription(raw.description());
        txn.setStatus(raw.status() == null ? "POSTED" : raw.status());
        return txn;
    }

    /** Chunked saves so a 10,000-row load does not build one enormous flush. */
    private void persistInBatches(List<Transaction> transactions) {
        for (int i = 0; i < transactions.size(); i += PERSIST_BATCH) {
            int end = Math.min(i + PERSIST_BATCH, transactions.size());
            transactionRepository.saveAll(transactions.subList(i, end));
            transactionRepository.flush();
        }
    }

    private IngestionBatch startBatch(String entityType, String source, int total) {
        IngestionBatch batch = new IngestionBatch();
        batch.setBatchRef("BATCH-%s-%s".formatted(
                LocalDate.now().atTime(LocalDateTime.now().toLocalTime()).format(REF_DATE),
                UUID.randomUUID().toString().substring(0, 4).toUpperCase()));
        batch.setEntityType(entityType);
        batch.setSource(source);
        batch.setTotalRecords(total);
        batch.setInitiatedBy(currentUser.username());
        return batchRepository.saveAndFlush(batch);
    }

    private void finishBatch(IngestionBatch batch, int accepted, int rejected,
                             int alerts, long durationMs) {
        batch.setAcceptedRecords(accepted);
        batch.setRejectedRecords(rejected);
        batch.setAlertsGenerated(alerts);
        batch.setStatus(rejected == 0 ? BatchStatus.COMPLETED : BatchStatus.COMPLETED_WITH_ERRORS);
        batch.setFinishedAt(Instant.now());
        batch.setDurationMs(durationMs);
        batchRepository.save(batch);

        auditService.record(AuditAction.ENTITY_INGESTION, batch.getBatchRef(),
                AuditAction.INGESTION_COMPLETED,
                "%d accepted, %d rejected, %d alerts in %d ms"
                        .formatted(accepted, rejected, alerts, durationMs));
    }

    @Transactional(readOnly = true)
    public IngestionBatch requireBatch(String batchRef) {
        return batchRepository.findByBatchRef(batchRef)
                .orElseThrow(() -> new com.meridiantrust.sentinel.common.error.ApiException
                        .NotFound("IngestionBatch", batchRef));
    }

    @Transactional(readOnly = true)
    public List<IngestionError> errorsFor(Long batchId) {
        return errorRepository.findByBatchIdOrderByRowNumberAsc(batchId);
    }
}
