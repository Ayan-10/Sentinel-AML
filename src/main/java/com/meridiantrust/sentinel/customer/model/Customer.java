package com.meridiantrust.sentinel.customer.model;

import com.meridiantrust.sentinel.common.model.RiskRating;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** KYC master record. Root of the customer → account → transaction chain. */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @Column(name = "customer_id", length = 32)
    private String customerId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    /** Business rule 8: PII — masked in list views. */
    @Column(name = "national_id", length = 32)
    private String nationalId;

    private String city;
    private String state;

    @Column(nullable = false, length = 2)
    private String country = "IN";

    @Column(name = "postal_code")
    private String postalCode;

    private String occupation;

    @Column(name = "annual_income")
    private BigDecimal annualIncome;

    @Column(name = "marital_status")
    private String maritalStatus;

    @Column(name = "education_level")
    private String educationLevel;

    @Column(name = "employment_status")
    private String employmentStatus;

    @Column(name = "customer_since")
    private LocalDate customerSince;

    @Column(name = "customer_segment")
    private String customerSegment;

    @Column(name = "kyc_status", nullable = false)
    private String kycStatus = "PENDING";

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_rating", nullable = false, length = 16)
    private RiskRating riskRating = RiskRating.LOW;

    @Column(name = "is_politically_exposed", nullable = false)
    private boolean politicallyExposed;

    @Column(name = "preferred_channel")
    private String preferredChannel;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Column(name = "num_complaints_last_year", nullable = false)
    private int numComplaintsLastYear;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Transient
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
