package com.meridiantrust.sentinel.sar.service;

import com.meridiantrust.sentinel.account.model.Account;
import com.meridiantrust.sentinel.account.repository.AccountRepository;
import com.meridiantrust.sentinel.alerting.model.Alert;
import com.meridiantrust.sentinel.alerting.service.AlertService;
import com.meridiantrust.sentinel.casemanagement.model.CaseFile;
import com.meridiantrust.sentinel.casemanagement.service.CaseService;
import com.meridiantrust.sentinel.common.audit.model.AuditAction;
import com.meridiantrust.sentinel.common.audit.service.AuditService;
import com.meridiantrust.sentinel.common.config.SentinelProperties;
import com.meridiantrust.sentinel.common.error.ApiException;
import com.meridiantrust.sentinel.common.security.CurrentUser;
import com.meridiantrust.sentinel.common.security.Roles;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.customer.repository.CustomerRepository;
import com.meridiantrust.sentinel.sar.model.AlertEvidence;
import com.meridiantrust.sentinel.sar.model.SarDraft;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import com.meridiantrust.sentinel.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles a draft Suspicious Activity Report from a case.
 *
 * <p>The system already holds everything a SAR needs — the detection engine
 * produced the findings, the alerts cite their evidence, and the case groups
 * them against one customer. This service does the clerical work of gathering
 * those pieces; {@link SarNarrativeComposer} writes the prose.
 *
 * <p><b>Read-only.</b> Generating a draft creates no rows and changes no state,
 * so it cannot disturb an alert, a case or a disposition. The one side effect is
 * an audit entry, which is deliberate: a SAR draft exposes unmasked PII, so who
 * produced one and when is a compliance question in its own right.
 */
@Service
public class SarDraftService {

    private static final Logger log = LoggerFactory.getLogger(SarDraftService.class);
    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String INSTITUTION = "MeridianTrust Bank";

    private final CaseService caseService;
    private final AlertService alertService;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final SarNarrativeComposer composer;
    private final AuditService auditService;
    private final CurrentUser currentUser;
    private final String baseCurrency;

    public SarDraftService(CaseService caseService,
                           AlertService alertService,
                           CustomerRepository customerRepository,
                           AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           SarNarrativeComposer composer,
                           AuditService auditService,
                           CurrentUser currentUser,
                           SentinelProperties properties) {
        this.caseService = caseService;
        this.alertService = alertService;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.composer = composer;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.baseCurrency = properties.baseCurrency();
    }

    /**
     * Builds the draft for a case.
     *
     * <p>Restricted to SENIOR_ANALYST: the draft carries unmasked subject PII by
     * necessity — a report that masks its subject identifies nobody and is of no
     * use to a Financial Intelligence Unit. That makes this endpoint the same
     * kind of privileged read as the unmasked customer record (business rule 8),
     * and it is secured the same way, at the service layer as well as the URL.
     */
    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_SENIOR)
    public SarDraft generate(String caseRef) {
        CaseFile caseFile = caseService.requireByRef(caseRef);

        List<Alert> alerts = alertService.byCase(caseFile.getId());
        if (alerts.isEmpty()) {
            throw new ApiException.Validation(
                    "Case %s has no alerts, so there is no evidence to report.".formatted(caseRef));
        }

        Customer customer = customerRepository.findById(caseFile.getCustomerId())
                .orElseThrow(() -> new ApiException.NotFound("Customer", caseFile.getCustomerId()));

        List<Transaction> evidence = loadEvidence(alerts);
        List<SarDraft.AccountSummary> accounts = accountsInvolved(customer.getCustomerId(), evidence);
        List<AlertEvidence> findings = toFindings(alerts);

        SarDraft.Subject subject = toSubject(customer);
        SarDraft.ActivitySummary activity = toActivity(caseFile, alerts, evidence);

        String narrative = composer.compose(subject, activity, accounts, findings);
        String recommendation = composer.recommendAction(
                caseFile.getDisposition() == null ? null : caseFile.getDisposition().name(),
                activity.highestRiskScore());

        SarDraft draft = new SarDraft(
                generateRef(),
                caseFile.getCaseRef(),
                Instant.now(),
                currentUser.username(),
                INSTITUTION,
                subject,
                accounts,
                activity,
                narrative,
                toSchedule(evidence, alerts),
                findings.stream().map(AlertEvidence::typology).distinct().sorted().toList(),
                alerts.stream().map(Alert::getAlertRef).sorted().toList(),
                recommendation);

        // A draft exposes unmasked PII; who generated one is itself auditable.
        auditService.record(AuditAction.ENTITY_CASE, caseRef, AuditAction.SAR_DRAFT_GENERATED,
                "SAR draft %s generated over %d alert(s) and %d transaction(s)"
                        .formatted(draft.draftRef(), alerts.size(), evidence.size()));

        log.info("SAR draft {} generated for case {} by {}",
                draft.draftRef(), caseRef, currentUser.username());
        return draft;
    }

    // --- assembly ----------------------------------------------------------

    /** Every transaction cited by any alert on the case, de-duplicated and time-ordered. */
    private List<Transaction> loadEvidence(List<Alert> alerts) {
        Set<String> ids = alerts.stream()
                .flatMap(a -> a.evidenceList().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return List.of();
        }
        return transactionRepository.findByTransactionIdIn(ids).stream()
                .sorted(Comparator.comparing(Transaction::getTxnTimestamp))
                .toList();
    }

    private SarDraft.Subject toSubject(Customer c) {
        String address = joinNonBlank(", ", c.getCity(), c.getState(), c.getCountry(), c.getPostalCode());

        return new SarDraft.Subject(
                c.getCustomerId(), c.getFullName(), c.getNationalId(), c.getDateOfBirth(),
                address, c.getOccupation(), c.getCustomerSegment(),
                c.getKycStatus(), c.getRiskRating().name(), c.isPoliticallyExposed(),
                c.getCustomerSince());
    }

    /** Only the accounts the cited activity actually touched, not every account the customer holds. */
    private List<SarDraft.AccountSummary> accountsInvolved(String customerId,
                                                           List<Transaction> evidence) {
        Set<String> involved = evidence.stream()
                .map(Transaction::getAccountId)
                .collect(Collectors.toSet());

        return accountRepository.findByCustomerId(customerId).stream()
                .filter(a -> involved.isEmpty() || involved.contains(a.getAccountId()))
                .map(a -> new SarDraft.AccountSummary(
                        a.getAccountId(), a.getAccountType(), a.getCurrency(), a.getOpenDate(),
                        joinNonBlank(" / ", a.getBranchCode(), a.getBranchCity())))
                .toList();
    }

    private SarDraft.ActivitySummary toActivity(CaseFile caseFile,
                                                List<Alert> alerts,
                                                List<Transaction> evidence) {
        LocalDateTime start = evidence.stream().map(Transaction::getTxnTimestamp)
                .min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime end = evidence.stream().map(Transaction::getTxnTimestamp)
                .max(LocalDateTime::compareTo).orElse(null);
        BigDecimal total = evidence.stream().map(Transaction::getAmountBase)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SarDraft.ActivitySummary(
                start, end, evidence.size(), total, baseCurrency, alerts.size(),
                alerts.stream().mapToInt(Alert::getRiskScore).max().orElse(0),
                caseFile.getDisposition() == null ? null : caseFile.getDisposition().name());
    }

    private List<AlertEvidence> toFindings(List<Alert> alerts) {
        return alerts.stream()
                // Highest risk first: a reader should meet the strongest finding first.
                .sorted(Comparator.comparingInt(Alert::getRiskScore).reversed())
                .map(a -> new AlertEvidence(
                        a.getAlertRef(), a.getRuleCode(), a.getTypology(), a.getRiskScore(),
                        a.getSeverity().name(), a.getExplanation(), a.evidenceList().size()))
                .toList();
    }

    private List<SarDraft.TransactionLine> toSchedule(List<Transaction> evidence,
                                                      List<Alert> alerts) {
        // Invert alert -> evidence once so each line can name the alerts citing it.
        Map<String, List<String>> citedBy = new HashMap<>();
        for (Alert alert : alerts) {
            for (String txnId : alert.evidenceList()) {
                citedBy.computeIfAbsent(txnId, k -> new ArrayList<>()).add(alert.getAlertRef());
            }
        }

        return evidence.stream()
                .map(t -> new SarDraft.TransactionLine(
                        t.getTransactionId(), t.getTxnTimestamp(), t.getAccountId(),
                        t.getDirection().name(), t.getAmountBase(), t.getAmount(), t.getCurrency(),
                        t.getChannel(), t.getCounterpartyName(), t.getCounterpartyCountry(),
                        citedBy.getOrDefault(t.getTransactionId(), List.of())))
                .toList();
    }

    private String generateRef() {
        return "SAR-DRAFT-%s-%s".formatted(
                LocalDate.now().format(REF_DATE),
                UUID.randomUUID().toString().substring(0, 6).toUpperCase());
    }

    /** Joins the non-blank parts, or returns null when nothing is present. */
    private String joinNonBlank(String separator, String... parts) {
        String joined = Arrays.stream(parts)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(separator));
        return joined.isBlank() ? null : joined;
    }
}
