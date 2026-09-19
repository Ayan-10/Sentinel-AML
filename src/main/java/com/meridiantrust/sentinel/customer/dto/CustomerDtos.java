package com.meridiantrust.sentinel.customer.dto;

import com.meridiantrust.sentinel.alerting.model.Alert;
import com.meridiantrust.sentinel.customer.model.Customer;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Business rule 8 in DTO form: two distinct projections of one entity.
 *
 * <p>{@link Masked} is what any analyst sees in a list. {@link Full} carries
 * real PII and is reachable only through an endpoint restricted to
 * SENIOR_ANALYST and above. Because they are separate types rather than one
 * type with a flag, it is impossible to return the wrong fidelity by forgetting
 * to set something.
 */
public final class CustomerDtos {

    private CustomerDtos() {}

    @Schema(description = "Customer with PII masked — safe for list views and any authenticated analyst.")
    public record Masked(
            String customerId,
            @Schema(example = "K****** S*****") String nameMasked,
            @Schema(example = "*****4821") String nationalIdMasked,
            @Schema(example = "k******@g****.com") String emailMasked,
            @Schema(example = "+91-******2955") String phoneMasked,
            String city,
            String country,
            String customerSegment,
            String kycStatus,
            String riskRating,
            boolean politicallyExposed) {}

    @Schema(description = "Full customer KYC detail. Requires SENIOR_ANALYST (business rule 8).")
    public record Full(
            String customerId,
            String firstName,
            String lastName,
            String fullName,
            String nationalId,
            String email,
            String phoneNumber,
            LocalDate dateOfBirth,
            String gender,
            String city,
            String state,
            String country,
            String postalCode,
            String occupation,
            BigDecimal annualIncome,
            String employmentStatus,
            LocalDate customerSince,
            String customerSegment,
            String kycStatus,
            String riskRating,
            boolean politicallyExposed,
            int numComplaintsLastYear) {}

    @Schema(description = "Chronological transaction activity for the customer timeline view.")
    public record TimelineEntry(
            String transactionId,
            LocalDateTime timestamp,
            String accountId,
            String direction,
            BigDecimal amount,
            String currency,
            BigDecimal amountBase,
            String channel,
            String counterpartyName,
            String counterpartyCountry,
            @Schema(description = "Alert references this transaction is evidence for")
            List<String> alertRefs) {}
}
