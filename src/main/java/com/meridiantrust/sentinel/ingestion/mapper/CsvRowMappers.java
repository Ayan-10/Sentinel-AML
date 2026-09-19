package com.meridiantrust.sentinel.ingestion.mapper;

import com.meridiantrust.sentinel.account.model.Account;
import com.meridiantrust.sentinel.common.model.RiskRating;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.ingestion.model.RawTransaction;
import com.meridiantrust.sentinel.ingestion.util.TimestampParser;

import java.math.BigDecimal;
import java.util.Map;

import static com.meridiantrust.sentinel.ingestion.util.CsvSupport.*;

/**
 * Maps CSV rows onto domain objects.
 *
 * <p>Column names follow the supplied {@code docs/customers.csv} and
 * {@code docs/accounts.csv} headers, which define the integration contract with
 * the core banking export. Mapping lives in the infrastructure layer so the
 * domain never learns what a CSV column is called — change the export format
 * and only this file moves.
 */
public final class CsvRowMappers {

    private CsvRowMappers() {}

    public static Customer toCustomer(Map<String, String> row) {
        Customer c = new Customer();
        c.setCustomerId(get(row, "customer_id"));
        c.setFirstName(get(row, "first_name", "Unknown"));
        c.setLastName(get(row, "last_name", "Unknown"));
        c.setGender(get(row, "gender"));
        c.setDateOfBirth(TimestampParser.parseDate(get(row, "date_of_birth")));
        c.setEmail(get(row, "email"));
        c.setPhoneNumber(get(row, "phone_number"));
        // The supplied export carries no national ID column, but business rule
        // 8 names ID numbers as PII requiring masking. A deterministic synthetic
        // value keeps the masking path exercised without inventing real PII.
        c.setNationalId(syntheticNationalId(get(row, "customer_id")));
        c.setCity(get(row, "city"));
        c.setState(get(row, "state"));
        c.setCountry(get(row, "country", "IN"));
        c.setPostalCode(get(row, "postal_code"));
        c.setOccupation(get(row, "occupation"));
        c.setAnnualIncome(decimal(row, "annual_income"));
        c.setMaritalStatus(get(row, "marital_status"));
        c.setEducationLevel(get(row, "education_level"));
        c.setEmploymentStatus(get(row, "employment_status"));
        c.setCustomerSince(TimestampParser.parseDate(get(row, "customer_since")));
        c.setCustomerSegment(get(row, "customer_segment"));
        c.setKycStatus(get(row, "kyc_status", "PENDING"));
        c.setRiskRating(RiskRating.fromNullable(get(row, "risk_rating")));
        c.setPoliticallyExposed(bool(row, "is_politically_exposed"));
        c.setPreferredChannel(get(row, "preferred_channel"));
        c.setEmailVerified(bool(row, "email_verified"));
        c.setPhoneVerified(bool(row, "phone_verified"));
        Integer complaints = integer(row, "num_complaints_last_year");
        c.setNumComplaintsLastYear(complaints == null ? 0 : complaints);
        return c;
    }

    public static Account toAccount(Map<String, String> row) {
        Account a = new Account();
        a.setAccountId(get(row, "account_id"));
        a.setCustomerId(get(row, "customer_id"));
        a.setAccountType(get(row, "account_type", "SAVINGS"));
        a.setAccountStatus(get(row, "account_status", "ACTIVE"));
        a.setCurrency(get(row, "currency", "INR"));
        a.setOpenDate(TimestampParser.parseDate(get(row, "open_date")));
        a.setCloseDate(TimestampParser.parseDate(get(row, "close_date")));
        a.setBranchCode(get(row, "branch_code"));
        a.setBranchCity(get(row, "branch_city"));
        BigDecimal balance = decimal(row, "current_balance");
        a.setCurrentBalance(balance == null ? BigDecimal.ZERO : balance);
        a.setAvgMonthlyBalance6m(decimal(row, "avg_monthly_balance_6m"));
        a.setCreditLimit(decimal(row, "credit_limit"));
        a.setCreditUtilizationPct(decimal(row, "credit_utilization_pct"));
        a.setOverdraftEnabled(bool(row, "overdraft_enabled"));
        a.setCardType(get(row, "card_type"));
        a.setJointAccount(bool(row, "is_joint_account"));
        a.setNumLinkedDevices(integer(row, "num_linked_devices"));
        a.setMobileBankingEnrolled(bool(row, "mobile_banking_enrolled"));
        a.setLastLoginDate(TimestampParser.parseDate(get(row, "last_login_date")));
        a.setAvgMonthlyTxnCount(integer(row, "avg_monthly_txn_count"));
        a.setAccountTier(get(row, "account_tier"));
        a.setRiskRating(RiskRating.fromNullable(get(row, "risk_rating")));
        return a;
    }

    public static RawTransaction toRawTransaction(Map<String, String> row) {
        return new RawTransaction(
                get(row, "transaction_id"),
                get(row, "account_id"),
                get(row, "customer_id"),
                get(row, "txn_timestamp", get(row, "transaction_date")),
                get(row, "direction"),
                get(row, "amount"),
                get(row, "currency", "INR"),
                get(row, "channel"),
                get(row, "txn_type"),
                get(row, "counterparty_name"),
                get(row, "counterparty_account"),
                get(row, "counterparty_bank"),
                get(row, "counterparty_country"),
                get(row, "description"),
                get(row, "status"),
                row.toString());
    }

    /**
     * Deterministic synthetic identifier — same customer always yields the same
     * value, so masking output is stable across reloads. Never derived from
     * anything real.
     */
    private static String syntheticNationalId(String customerId) {
        if (customerId == null) {
            return null;
        }
        int hash = Math.abs(customerId.hashCode());
        return "IDN%04d%04d".formatted(hash % 10000, (hash / 10000) % 10000);
    }
}
