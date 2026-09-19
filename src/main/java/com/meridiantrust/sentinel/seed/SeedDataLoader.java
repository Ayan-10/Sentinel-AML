package com.meridiantrust.sentinel.seed;

import com.meridiantrust.sentinel.account.model.Account;
import com.meridiantrust.sentinel.account.repository.AccountRepository;
import com.meridiantrust.sentinel.common.config.SentinelProperties;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.customer.repository.CustomerRepository;
import com.meridiantrust.sentinel.ingestion.model.IngestionResult;
import com.meridiantrust.sentinel.ingestion.service.IngestionService;
import com.meridiantrust.sentinel.ingestion.model.RawTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads synthetic data on first startup so a cold {@code docker compose up}
 * produces a system with alerts already in the queue — demo-ready with no
 * manual step.
 *
 * <p>Idempotent by design: it checks whether customers already exist and does
 * nothing if so. A container restart must not duplicate the dataset.
 *
 * <p>Transactions are loaded <em>through the real ingestion path</em> rather
 * than written straight to the repositories. That costs a little startup time
 * and buys two things: the seed exercises validation, normalisation and
 * detection exactly as production traffic would, and the logged duration is a
 * genuine measurement of the bulk performance NFR rather than a claim.
 */
@Component
@Order(100)
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final SentinelProperties properties;
    private final SyntheticDataGenerator generator;
    private final TypologyPlanter planter;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final IngestionService ingestionService;

    public SeedDataLoader(SentinelProperties properties,
                          SyntheticDataGenerator generator,
                          TypologyPlanter planter,
                          CustomerRepository customerRepository,
                          AccountRepository accountRepository,
                          IngestionService ingestionService) {
        this.properties = properties;
        this.generator = generator;
        this.planter = planter;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seed().enabled()) {
            log.info("Seed data disabled (sentinel.seed.enabled=false) — skipping.");
            return;
        }
        if (customerRepository.count() > 0) {
            log.info("Database already contains {} customers — skipping seed load.",
                    customerRepository.count());
            return;
        }

        long start = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        List<Customer> customers = generator.generateCustomers(properties.seed().customers());
        customerRepository.saveAll(customers);
        List<Account> accounts = generator.generateAccounts(customers);
        accountRepository.saveAll(accounts);
        log.info("Seeded {} customers and {} accounts", customers.size(), accounts.size());

        // Plant the six laundering typologies first so their account histories
        // exist before the benign traffic is interleaved around them.
        List<TypologyPlanter.PlantedScenario> scenarios = planter.plantAll(accounts, now);
        List<RawTransaction> transactions = new ArrayList<>();
        for (TypologyPlanter.PlantedScenario scenario : scenarios) {
            transactions.addAll(scenario.transactions());
            log.info("Planted typology '{}' (expects rule {}) on {} / {} — {} transactions",
                    scenario.label(), scenario.expectedRule(), scenario.customerId(),
                    scenario.accountId(), scenario.transactions().size());
        }

        int benignCount = Math.max(0, properties.seed().transactions() - transactions.size());
        transactions.addAll(generator.generateBenignTransactions(accounts, benignCount, now));

        log.info("Ingesting {} seed transactions through the live ingestion path "
                + "(validation → FX normalisation → detection)...", transactions.size());
        IngestionResult result = ingestionService.ingest(transactions, "seed-bootstrap", true);

        long totalMs = System.currentTimeMillis() - start;
        log.info("""

                ================================================================
                  SENTINEL SEED COMPLETE
                  Customers       : {}
                  Accounts        : {}
                  Transactions    : {} accepted, {} rejected
                  Alerts created  : {}
                  Detection time  : {} ms   (NFR target: 10,000 txns < 120,000 ms)
                  Total seed time : {} ms
                  Planted typologies: {}
                ================================================================
                """,
                customers.size(), accounts.size(), result.acceptedRecords(),
                result.rejectedRecords(), result.alertsGenerated(), result.durationMs(),
                totalMs, scenarios.stream().map(TypologyPlanter.PlantedScenario::label).toList());
    }
}
