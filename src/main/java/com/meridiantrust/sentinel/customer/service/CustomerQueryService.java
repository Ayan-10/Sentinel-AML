package com.meridiantrust.sentinel.customer.service;

import com.meridiantrust.sentinel.alerting.model.Alert;
import com.meridiantrust.sentinel.alerting.repository.AlertRepository;
import com.meridiantrust.sentinel.common.error.ApiException;
import com.meridiantrust.sentinel.common.security.PiiMasker;
import com.meridiantrust.sentinel.common.security.Roles;
import com.meridiantrust.sentinel.customer.dto.CustomerDtos;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.customer.repository.CustomerRepository;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import com.meridiantrust.sentinel.transaction.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer read model, and the enforcement point for business rule 8.
 *
 * <p>The authorisation difference between {@link #masked} and {@link #full} is
 * declared here on the service, not only on the controller mapping. Anything
 * that wants unmasked PII must pass a SENIOR_ANALYST check, whatever route it
 * arrives by.
 */
@Service
public class CustomerQueryService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final PiiMasker masker;

    public CustomerQueryService(CustomerRepository customerRepository,
                                TransactionRepository transactionRepository,
                                AlertRepository alertRepository,
                                PiiMasker masker) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.masker = masker;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_ANALYST)
    public CustomerDtos.Masked masked(String customerId) {
        Customer c = require(customerId);
        return new CustomerDtos.Masked(
                c.getCustomerId(),
                masker.maskName(c.getFullName()),
                masker.maskIdentifier(c.getNationalId()),
                masker.maskEmail(c.getEmail()),
                masker.maskPhone(c.getPhoneNumber()),
                c.getCity(), c.getCountry(), c.getCustomerSegment(),
                c.getKycStatus(), c.getRiskRating().name(), c.isPoliticallyExposed());
    }

    /** Business rule 8: unmasked PII requires an authorised role. */
    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_SENIOR)
    public CustomerDtos.Full full(String customerId) {
        Customer c = require(customerId);
        return new CustomerDtos.Full(
                c.getCustomerId(), c.getFirstName(), c.getLastName(), c.getFullName(),
                c.getNationalId(), c.getEmail(), c.getPhoneNumber(), c.getDateOfBirth(),
                c.getGender(), c.getCity(), c.getState(), c.getCountry(), c.getPostalCode(),
                c.getOccupation(), c.getAnnualIncome(), c.getEmploymentStatus(),
                c.getCustomerSince(), c.getCustomerSegment(), c.getKycStatus(),
                c.getRiskRating().name(), c.isPoliticallyExposed(), c.getNumComplaintsLastYear());
    }

    /**
     * Transaction timeline, annotating each transaction with the alerts it is
     * evidence for — so an analyst can see the flagged activity in the context
     * of everything around it, which is where laundering patterns become
     * legible.
     */
    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_ANALYST)
    public List<CustomerDtos.TimelineEntry> timeline(String customerId, int limit) {
        require(customerId);

        List<Transaction> transactions = transactionRepository
                .findByCustomerIdOrderByTxnTimestampDesc(customerId,
                        PageRequest.of(0, Math.min(limit, 500)))
                .getContent();

        // Invert the alert→evidence relation once, rather than scanning the
        // alert list for every transaction.
        Map<String, List<String>> alertsByTxn = new HashMap<>();
        for (Alert alert : alertRepository.findByCustomerIdOrderByRiskScoreDesc(customerId)) {
            for (String txnId : alert.evidenceList()) {
                alertsByTxn.computeIfAbsent(txnId, k -> new java.util.ArrayList<>())
                        .add(alert.getAlertRef());
            }
        }

        return transactions.stream()
                .map(t -> new CustomerDtos.TimelineEntry(
                        t.getTransactionId(), t.getTxnTimestamp(), t.getAccountId(),
                        t.getDirection().name(), t.getAmount(), t.getCurrency(),
                        t.getAmountBase(), t.getChannel(), t.getCounterpartyName(),
                        t.getCounterpartyCountry(),
                        alertsByTxn.getOrDefault(t.getTransactionId(), List.of())))
                .toList();
    }

    private Customer require(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ApiException.NotFound("Customer", customerId));
    }
}
