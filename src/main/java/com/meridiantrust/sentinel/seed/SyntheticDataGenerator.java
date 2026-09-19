package com.meridiantrust.sentinel.seed;

import com.meridiantrust.sentinel.account.model.Account;
import com.meridiantrust.sentinel.common.model.RiskRating;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.ingestion.model.RawTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates realistic synthetic customers, accounts and transactions.
 *
 * <p>NFR (Data privacy): no real PII. Names are drawn from fixed pools and
 * combined arbitrarily; identifiers are derived from the sequence number.
 *
 * <p>Deliverable D3 asks for at least three laundering typologies in the seed
 * data. We plant <b>six</b> — one per detection rule — so every rule has a
 * guaranteed demonstration case and a reviewer can verify each independently
 * rather than taking the engine's word for it.
 *
 * <p>The remaining ~95% of the data is benign background traffic. A seed file
 * where everything alerts would prove the rules fire, but nothing about whether
 * they discriminate — and false-positive rate is the metric that decides
 * whether an AML system is usable.
 */
@Component
public class SyntheticDataGenerator {

    private static final String[] FIRST_NAMES = {
            "Krishna", "Anika", "Rohan", "Priya", "Arjun", "Meera", "Vikram", "Divya",
            "Sanjay", "Kavya", "Rahul", "Nisha", "Aditya", "Sneha", "Karthik", "Pooja",
            "Vivek", "Anjali", "Suresh", "Lakshmi", "Manish", "Deepa", "Rajesh", "Swati"};

    private static final String[] LAST_NAMES = {
            "Sharma", "Fernandes", "Patel", "Reddy", "Iyer", "Nair", "Gupta", "Singh",
            "Mehta", "Desai", "Rao", "Kulkarni", "Chatterjee", "Bose", "Malhotra", "Joshi"};

    private static final String[] CITIES = {
            "Gurugram", "Bengaluru", "Mumbai", "Pune", "Hyderabad", "Chennai", "Delhi", "Kolkata"};

    private static final String[] STATES = {
            "Haryana", "Karnataka", "Maharashtra", "Maharashtra", "Telangana",
            "Tamil Nadu", "Delhi", "West Bengal"};

    private static final String[] SEGMENTS = {"RETAIL", "PREMIUM", "BUSINESS", "PRIVATE"};
    private static final String[] ACCOUNT_TYPES = {"SAVINGS", "CURRENT", "NRE", "SALARY"};
    private static final String[] CHANNELS = {"NEFT", "RTGS", "UPI", "IMPS", "BRANCH", "ATM", "SWIFT"};
    private static final String[] BENIGN_COUNTERPARTIES = {
            "Reliance Retail Ltd", "Tata Consultancy Services", "Flipkart Internet Pvt Ltd",
            "Bharat Petroleum", "Apollo Hospitals", "HDFC Life Insurance", "Zomato Ltd",
            "Croma Electronics", "Indian Railways IRCTC", "Bigbasket"};

    /** Deterministic seed — the same dataset every run, so demos are reproducible. */
    private final Random random = new Random(20260919L);

    public List<Customer> generateCustomers(int count) {
        List<Customer> customers = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            customers.add(buildCustomer(i));
        }
        return customers;
    }

    private Customer buildCustomer(int index) {
        String id = "CUST_%05d".formatted(index);
        int cityIdx = random.nextInt(CITIES.length);

        Customer c = new Customer();
        c.setCustomerId(id);
        c.setFirstName(FIRST_NAMES[random.nextInt(FIRST_NAMES.length)]);
        c.setLastName(LAST_NAMES[random.nextInt(LAST_NAMES.length)]);
        c.setGender(random.nextBoolean() ? "M" : "F");
        c.setDateOfBirth(LocalDate.of(1960 + random.nextInt(45),
                1 + random.nextInt(12), 1 + random.nextInt(28)));
        c.setEmail("%s.%s%d@example.test".formatted(
                c.getFirstName().toLowerCase(), c.getLastName().toLowerCase(), index));
        c.setPhoneNumber("+91-%010d".formatted(6000000000L + random.nextInt(999999999)));
        c.setNationalId("IDN%08d".formatted(10000000 + index * 7919 % 89999999));
        c.setCity(CITIES[cityIdx]);
        c.setState(STATES[cityIdx]);
        c.setCountry("IN");
        c.setPostalCode("%06d".formatted(100000 + random.nextInt(899999)));
        c.setOccupation(random.nextBoolean() ? "Salaried" : "Self-Employed");
        c.setAnnualIncome(BigDecimal.valueOf(250000 + random.nextInt(4750000))
                .setScale(2, RoundingMode.HALF_UP));
        c.setMaritalStatus(random.nextBoolean() ? "Married" : "Single");
        c.setEducationLevel(random.nextBoolean() ? "Graduate" : "Postgraduate");
        c.setEmploymentStatus("EMPLOYED");
        c.setCustomerSince(LocalDate.of(2015 + random.nextInt(10),
                1 + random.nextInt(12), 1 + random.nextInt(28)));
        c.setCustomerSegment(SEGMENTS[random.nextInt(SEGMENTS.length)]);
        c.setKycStatus(random.nextInt(20) == 0 ? "PENDING" : "VERIFIED");

        // Realistic risk distribution: mostly low, a meaningful minority medium,
        // a small high-risk tail. A uniform split would make the risk-rating
        // score uplift meaningless.
        int roll = random.nextInt(100);
        c.setRiskRating(roll < 65 ? RiskRating.LOW : roll < 92 ? RiskRating.MEDIUM : RiskRating.HIGH);
        c.setPoliticallyExposed(random.nextInt(100) < 3);
        c.setPreferredChannel(random.nextBoolean() ? "Mobile Banking" : "Internet Banking");
        c.setEmailVerified(true);
        c.setPhoneVerified(true);
        c.setNumComplaintsLastYear(random.nextInt(3));
        return c;
    }

    public List<Account> generateAccounts(List<Customer> customers) {
        List<Account> accounts = new ArrayList<>();
        int accountSeq = 1;
        for (Customer c : customers) {
            int accountCount = 1 + random.nextInt(2);   // 1-2 accounts per customer
            for (int i = 0; i < accountCount; i++) {
                accounts.add(buildAccount(accountSeq++, c));
            }
        }
        return accounts;
    }

    private Account buildAccount(int seq, Customer owner) {
        Account a = new Account();
        a.setAccountId("ACC_%06d".formatted(seq));
        a.setCustomerId(owner.getCustomerId());
        a.setAccountType(ACCOUNT_TYPES[random.nextInt(ACCOUNT_TYPES.length)]);
        a.setAccountStatus("ACTIVE");
        a.setCurrency("INR");
        a.setOpenDate(owner.getCustomerSince());
        a.setBranchCode("BR%03d".formatted(100 + random.nextInt(99)));
        a.setBranchCity(owner.getCity());
        a.setCurrentBalance(BigDecimal.valueOf(5000 + random.nextInt(900000))
                .setScale(2, RoundingMode.HALF_UP));
        a.setAvgMonthlyBalance6m(a.getCurrentBalance()
                .multiply(BigDecimal.valueOf(0.8 + random.nextDouble() * 0.4))
                .setScale(2, RoundingMode.HALF_UP));
        a.setCreditLimit(BigDecimal.ZERO);
        a.setCreditUtilizationPct(BigDecimal.ZERO);
        a.setOverdraftEnabled(random.nextInt(5) == 0);
        a.setCardType(random.nextBoolean() ? "CLASSIC" : "GOLD");
        a.setJointAccount(false);
        a.setNumLinkedDevices(1 + random.nextInt(3));
        a.setMobileBankingEnrolled(true);
        a.setLastLoginDate(LocalDate.now().minusDays(random.nextInt(30)));
        a.setAvgMonthlyTxnCount(8 + random.nextInt(25));
        a.setAccountTier(random.nextBoolean() ? "SILVER" : "GOLD");
        a.setRiskRating(owner.getRiskRating());
        return a;
    }

    /** Ordinary, unremarkable activity — the majority of the dataset. */
    public List<RawTransaction> generateBenignTransactions(List<Account> accounts,
                                                           int count,
                                                           LocalDateTime endTime) {
        List<RawTransaction> transactions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Account account = accounts.get(random.nextInt(accounts.size()));
            // Spread over the last 120 days so behavioural baselines have history.
            LocalDateTime timestamp = endTime
                    .minusDays(random.nextInt(120))
                    .minusHours(random.nextInt(24))
                    .minusMinutes(random.nextInt(60));

            boolean credit = random.nextInt(100) < 45;
            // Log-ish distribution: many small amounts, few large ones.
            double magnitude = Math.pow(10, 2.2 + random.nextDouble() * 2.3);
            BigDecimal amount = BigDecimal.valueOf(magnitude)
                    .setScale(2, RoundingMode.HALF_UP);

            transactions.add(new RawTransaction(
                    "TXN_B%07d".formatted(i + 1),
                    account.getAccountId(),
                    account.getCustomerId(),
                    timestamp.toString(),
                    credit ? "CREDIT" : "DEBIT",
                    amount.toPlainString(),
                    "INR",
                    CHANNELS[random.nextInt(CHANNELS.length)],
                    credit ? "TRANSFER_IN" : "PURCHASE",
                    BENIGN_COUNTERPARTIES[random.nextInt(BENIGN_COUNTERPARTIES.length)],
                    "CPACC%08d".formatted(random.nextInt(99999999)),
                    "HDFC Bank",
                    "IN",
                    "Routine activity",
                    "POSTED",
                    "seed-benign"));
        }
        return transactions;
    }
}
