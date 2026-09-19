package com.meridiantrust.sentinel.account.model;

import com.meridiantrust.sentinel.common.model.RiskRating;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id
    @Column(name = "account_id", length = 32)
    private String accountId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "account_status", nullable = false)
    private String accountStatus = "ACTIVE";

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "open_date")
    private LocalDate openDate;

    @Column(name = "close_date")
    private LocalDate closeDate;

    @Column(name = "branch_code")
    private String branchCode;

    @Column(name = "branch_city")
    private String branchCity;

    @Column(name = "current_balance", nullable = false)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "avg_monthly_balance_6m")
    private BigDecimal avgMonthlyBalance6m;

    @Column(name = "credit_limit")
    private BigDecimal creditLimit;

    @Column(name = "credit_utilization_pct")
    private BigDecimal creditUtilizationPct;

    @Column(name = "overdraft_enabled", nullable = false)
    private boolean overdraftEnabled;

    @Column(name = "card_type")
    private String cardType;

    @Column(name = "is_joint_account", nullable = false)
    private boolean jointAccount;

    @Column(name = "num_linked_devices")
    private Integer numLinkedDevices;

    @Column(name = "mobile_banking_enrolled", nullable = false)
    private boolean mobileBankingEnrolled;

    @Column(name = "last_login_date")
    private LocalDate lastLoginDate;

    @Column(name = "avg_monthly_txn_count")
    private Integer avgMonthlyTxnCount;

    @Column(name = "account_tier")
    private String accountTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_rating", nullable = false, length = 16)
    private RiskRating riskRating = RiskRating.LOW;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
