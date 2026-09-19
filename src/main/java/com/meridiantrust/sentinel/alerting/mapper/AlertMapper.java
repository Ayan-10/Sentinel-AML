package com.meridiantrust.sentinel.alerting.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridiantrust.sentinel.alerting.dto.AlertDetailDto;
import com.meridiantrust.sentinel.alerting.dto.AlertSummaryDto;
import com.meridiantrust.sentinel.alerting.model.Alert;
import com.meridiantrust.sentinel.casemanagement.repository.CaseRepository;
import com.meridiantrust.sentinel.common.security.PiiMasker;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.customer.repository.CustomerRepository;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import com.meridiantrust.sentinel.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Entity-to-DTO translation, and the single place PII masking is applied to
 * alerts.
 *
 * <p>Centralising it means the masking rule cannot diverge between the queue
 * endpoint and any future export, report or notification path.
 */
@Component
public class AlertMapper {

    private static final TypeReference<Map<String, Integer>> BREAKDOWN_TYPE = new TypeReference<>() {};

    private final PiiMasker masker;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final CaseRepository caseRepository;
    private final ObjectMapper objectMapper;

    public AlertMapper(PiiMasker masker,
                       CustomerRepository customerRepository,
                       TransactionRepository transactionRepository,
                       CaseRepository caseRepository,
                       ObjectMapper objectMapper) {
        this.masker = masker;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.caseRepository = caseRepository;
        this.objectMapper = objectMapper;
    }

    /** Business rule 8: list projection — masked, for any authenticated analyst. */
    public AlertSummaryDto toSummary(Alert alert) {
        String name = customerRepository.findById(alert.getCustomerId())
                .map(Customer::getFullName)
                .orElse("Unknown");
        return new AlertSummaryDto(
                alert.getAlertRef(),
                alert.getCustomerId(),
                masker.maskName(name),
                masker.maskAccount(alert.getAccountId()),
                alert.getRuleCode(),
                alert.getTypology(),
                alert.getRiskScore(),
                alert.getSeverity().name(),
                alert.getStatus().name(),
                alert.getEvidenceAmountBase(),
                alert.evidenceList().size(),
                alert.getTriggerCount(),
                alert.getDisposition() == null ? null : alert.getDisposition().name(),
                alert.getFirstDetectedAt(),
                alert.getLastDetectedAt());
    }

    /**
     * Detail projection, carrying the evidence transactions themselves.
     *
     * <p>The customer name here is unmasked: reaching this endpoint already
     * required the ANALYST role, and an analyst cannot investigate a case
     * against an asterisked name. Identifiers such as the national ID remain
     * behind the senior-only customer endpoint.
     */
    public AlertDetailDto toDetail(Alert alert) {
        String name = customerRepository.findById(alert.getCustomerId())
                .map(Customer::getFullName)
                .orElse("Unknown");

        List<Transaction> evidence = alert.evidenceList().isEmpty()
                ? List.of()
                : transactionRepository.findByTransactionIdIn(alert.evidenceList());

        List<AlertDetailDto.EvidenceTransactionDto> evidenceDtos = evidence.stream()
                .sorted((a, b) -> a.getTxnTimestamp().compareTo(b.getTxnTimestamp()))
                .map(t -> new AlertDetailDto.EvidenceTransactionDto(
                        t.getTransactionId(),
                        t.getTxnTimestamp(),
                        t.getDirection().name(),
                        t.getAmount(),
                        t.getCurrency(),
                        t.getAmountBase(),
                        t.getChannel(),
                        t.getCounterpartyName(),
                        t.getCounterpartyCountry()))
                .toList();

        String caseRef = Optional.ofNullable(alert.getCaseId())
                .flatMap(caseRepository::findById)
                .map(c -> c.getCaseRef())
                .orElse(null);

        return new AlertDetailDto(
                alert.getAlertRef(),
                alert.getCustomerId(),
                name,
                alert.getAccountId(),
                alert.getRuleCode(),
                alert.getTypology(),
                alert.getRiskScore(),
                alert.getSeverity().name(),
                alert.getStatus().name(),
                alert.getExplanation(),
                parseBreakdown(alert.getScoreBreakdown()),
                evidenceDtos,
                alert.getEvidenceAmountBase(),
                alert.getTriggerCount(),
                alert.getDisposition() == null ? null : alert.getDisposition().name(),
                alert.getDispositionReason(),
                alert.getDisposedBy(),
                alert.getDisposedAt(),
                caseRef,
                alert.getFirstDetectedAt(),
                alert.getLastDetectedAt());
    }

    private Map<String, Integer> parseBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, BREAKDOWN_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
