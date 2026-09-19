# Sentinel AML — Deliverables Checklist

**For the reviewer.** Every required deliverable from the problem statement, mapped to the exact
file, endpoint or command that evidences it. Each row is independently verifiable — nothing here
asks you to take the implementation's word for anything.

Start the system first (`docker compose up --build`, or `./gradlew bootRun --args='--spring.profiles.active=local'`),
then work down this page.

---

## A. Required Deliverables

| # | Deliverable | Status | Evidence |
|---|---|---|---|
| **D1** | Working Spring Boot application, source in a Git repository | ⚠️ **See note** | App: `./gradlew bootRun` → boots in ~2.8 s. Repo initialised (`.git` present, `.gitignore` configured, 142 files staged) but **left uncommitted at the user's explicit instruction**. One `git commit` completes this row. |
| **D2** | Database schema / ERD with migration scripts | ✅ | Migrations: [`src/main/resources/db/migration/V1__core_schema.sql`](../src/main/resources/db/migration/V1__core_schema.sql), [`V2__reference_data.sql`](../src/main/resources/db/migration/V2__reference_data.sql). ERD: [README § Database schema & ERD](../README.md#database-schema--erd). Applied automatically by Flyway on startup. |
| **D3** | Seed/synthetic dataset — customers, accounts, transactions covering **≥ 3** laundering typologies | ✅ **6 typologies** | Data files: [`deliverables/seed-data/`](seed-data/) — 500 customers, 737 accounts, 10,000 transactions. Generator: [`seed/SyntheticDataGenerator.java`](../src/main/java/com/meridiantrust/sentinel/seed/SyntheticDataGenerator.java), typologies planted by [`seed/TypologyPlanter.java`](../src/main/java/com/meridiantrust/sentinel/seed/TypologyPlanter.java). See §C below. |
| **D4** | Documented REST API (OpenAPI/Swagger spec) | ✅ | Live: http://localhost:8080/swagger-ui.html · Spec file: [`deliverables/openapi.json`](openapi.json) — **28 paths, 35 schemas**, every endpoint annotated with roles, status codes and examples. |
| **D5** | Unit tests covering detection rule logic | ✅ **59 passing** | `./gradlew test`. Sources: [`src/test/java/.../detection/rules/`](../src/test/java/com/meridiantrust/sentinel/detection/rules/). Boundary matrix in §D below. |
| **D6** | README explaining **architecture**, **setup instructions**, and **rule configuration approach** | ✅ | [`README.md`](../README.md) — all three mandated sections present and self-contained: [Architecture](../README.md#architecture), [Setup](../README.md#setup-instructions), [Rule configuration](../README.md#rule-configuration-approach). |
| **D7** | Demo walkthrough showing ingestion → detection → alert → case disposition | ✅ | [`deliverables/DEMO.md`](DEMO.md) — a scripted, copy-pasteable walkthrough of the full flow against the seeded data. |
| **§C** | Frontend/dashboard — alert queue, risk heatmap, customer timeline, case detail (*optional but recommended* in the brief) | ✅ **Delivered** | **http://localhost:3000** — React 18 + Vite, served by `nginx` as the `sentinel-web` container. All four required views present; see [§C2](#c2-frontend--dashboard-problem-statement-c--optional-but-recommended). |
| **Ext.** | Extension idea — automated SAR draft generation (*extension ideas* in the brief) | ✅ **Delivered** | `GET /api/v1/cases/{ref}/sar-draft` · UI: Cases → **Generate SAR draft**. 1 of 6 extension ideas attempted; the other five are listed in [§G](#g-extension-ideas--status-of-all-six). |

> **D1 note:** the repository is initialised and every file is staged, but no commit exists because
> the user instructed "do not commit or push". This is the single outstanding item and it is one
> command away.

---

## B. Business rules — all nine implemented

| # | Business rule | Implementation | Verify |
|---|---|---|---|
| 1 | Any single txn ≥ $10,000 equivalent auto-flagged | [`CtrThresholdRule`](../src/main/java/com/meridiantrust/sentinel/detection/rule/impl/CtrThresholdRule.java) | `GET /api/v1/alerts?ruleCode=CTR_THRESHOLD` |
| 2 | 3+ txns of $9,000–9,999 from one account in 24h → Structuring | [`StructuringRule`](../src/main/java/com/meridiantrust/sentinel/detection/rule/impl/StructuringRule.java) | `GET /api/v1/alerts?ruleCode=STRUCTURING` |
| 3 | ≥80% of a deposit out within 48h → Rapid Movement | [`RapidMovementRule`](../src/main/java/com/meridiantrust/sentinel/detection/rule/impl/RapidMovementRule.java) | `GET /api/v1/alerts?ruleCode=RAPID_MOVEMENT` |
| 4 | Listed jurisdiction/counterparty alerts **regardless of amount** | [`HighRiskJurisdictionRule`](../src/main/java/com/meridiantrust/sentinel/detection/rule/impl/HighRiskJurisdictionRule.java) | `GET /api/v1/alerts?ruleCode=HIGH_RISK_JURISDICTION` — the top-scoring alert is an **₹850** transfer |
| 5 | Daily value > 3× the 90-day rolling average | [`BehavioralDeviationRule`](../src/main/java/com/meridiantrust/sentinel/detection/rule/impl/BehavioralDeviationRule.java) | `GET /api/v1/alerts?ruleCode=BEHAVIORAL_DEVIATION` |
| 6 | Alerts never silently deleted; disposition + reason + analyst retained | No delete path exists in [`AlertRepository`](../src/main/java/com/meridiantrust/sentinel/alerting/repository/AlertRepository.java); [`AuditLogRepository`](../src/main/java/com/meridiantrust/sentinel/common/audit/repository/AuditLogRepository.java) extends `Repository`, not `JpaRepository`, so no delete method exists to call | `GET /api/v1/alerts?status=CLOSED` returns `disposition`, `dispositionReason`, `disposedBy` |
| 7 | Weighted 0–100 risk score, higher sorts to the top | [`RiskScoringService`](../src/main/java/com/meridiantrust/sentinel/alerting/service/RiskScoringService.java) | `GET /api/v1/alerts?sortBy=riskScore&direction=desc`; every alert detail carries a `scoreBreakdown` |
| 8 | PII masked in list views, full only in detail for authorised roles | [`PiiMasker`](../src/main/java/com/meridiantrust/sentinel/common/security/PiiMasker.java) + two separate DTO types | `/customers/{id}` (any analyst, masked) vs `/customers/{id}/full` (**SENIOR only**, unmasked) |
| 9 | All amounts normalised to a base currency via a configurable rate table | [`Money`](../src/main/java/com/meridiantrust/sentinel/common/model/Money.java) + [`FxConversionService`](../src/main/java/com/meridiantrust/sentinel/reference/service/FxConversionService.java); `fx_rates` table | Every transaction carries `amountBase` and `fxRateApplied`; `GET /api/v1/admin/fx-rates` |

**Beyond the mandate:** a sixth typology, [`RoundAmountPatternRule`](../src/main/java/com/meridiantrust/sentinel/detection/rule/impl/RoundAmountPatternRule.java) (repeated round-number amounts), from the problem statement's detection-engine section.

---

## C. Laundering typologies in the seed data (D3 requires ≥ 3; six are planted)

Each is deliberately constructed so the corresponding rule has a guaranteed, inspectable case.
Run the command to confirm the alert exists.

| # | Typology | Planted as | Customer | Verify |
|---|---|---|---|---|
| 1 | **Structuring** | 5 deposits of ₹9,200–9,850 across 18 hours | `CUST_00026` | `curl -u analyst:analyst123 'localhost:8080/api/v1/alerts?customerId=CUST_00026&ruleCode=STRUCTURING'` |
| 2 | **Layering** | ₹850,000 in, 92% out to 4 offshore counterparties in 31h | `CUST_00078` | `...?customerId=CUST_00078&ruleCode=RAPID_MOVEMENT` |
| 3 | **Jurisdiction risk** | Transfers to a sanctioned entity, incl. an **₹850** test transfer | `CUST_00134` | `...?customerId=CUST_00134&ruleCode=HIGH_RISK_JURISDICTION` |
| 4 | **Behavioural deviation** | 90 days at ~₹15k/day, then a ₹620k day | `CUST_00210` | `...?customerId=CUST_00210&ruleCode=BEHAVIORAL_DEVIATION` |
| 5 | **Round amounts** | 6 exact multiples of ₹50,000 in one day | `CUST_00267` | `...?customerId=CUST_00267&ruleCode=ROUND_AMOUNT_PATTERN` |
| 6 | **CTR via FX** | A **USD** transfer that breaches only *after* normalisation | `CUST_00314` | `...?customerId=CUST_00314&ruleCode=CTR_THRESHOLD` |

The remaining ~95% of the dataset is benign background traffic. A seed file where everything
alerts would show the rules fire but nothing about whether they *discriminate* — and
false-positive rate is what decides whether an AML system is usable.

---

## C2. Frontend / dashboard (problem statement §C — *optional but recommended*)

**Delivered.** Open **http://localhost:3000** after `docker compose up --build`, and sign in as
`analyst`, `senior` or `admin`.

The brief names four views; all four are present:

| Required view | Where | Verify |
|---|---|---|
| **Alert queue** | *Alert queue* tab | Sorted by risk score descending, PII masked, filters for status/severity/rule |
| **Risk heatmap** | *Risk heatmap* tab | Customer × typology grid, 744 cells across 6 typologies |
| **Customer transaction timeline** | *Customer timeline* tab | Alert-evidence transactions flagged; try `CUST_00026`, `CUST_00078`, `CUST_00134` |
| **Case detail view** | *Cases* tab → select a case | Member alerts, narrative, disposition controls, immutable audit trail |

Stack: React 18 + Vite, hand-rolled CSS, **no component or charting library** (165 KB bundle,
52 KB gzipped). `nginx` proxies `/api` to the API container, so the browser sees one origin and
there is no CORS configuration to get wrong.

**RBAC holds through the UI path**, not just the API: verified through the nginx proxy that an
anonymous request gets `401` and an `analyst` hitting an admin endpoint gets `403`. The console
is not the thing enforcing access — the API is.

The heatmap uses a single-hue sequential ramp (light → dark) rather than a rainbow, so visual
order matches numeric order; every cell prints its score and every severity badge carries a text
label, so no value depends on colour alone.

Source: [`frontend/`](../frontend/) · Container: `sentinel-web` on port `3000`.

---

## C3. Extension delivered — automated SAR draft generation

The problem statement lists *"automated SAR (Suspicious Activity Report) draft generation
summarizing evidence in narrative form for regulatory filing"* among its extension ideas.
**Delivered.**

| | |
|---|---|
| Endpoint | `GET /api/v1/cases/{caseRef}/sar-draft` (JSON) · `/sar-draft/text` (printable) |
| Role | **SENIOR_ANALYST** — enforced at the URL *and* on the service method |
| Source | [`src/main/java/.../sar/`](../src/main/java/com/meridiantrust/sentinel/sar/) |
| UI | Cases tab → select a case → **Generate SAR draft** |
| Tests | 10 unit tests on the narrative composer |

Try it:

```bash
CREF=$(curl -s -u analyst:analyst123 'http://localhost:8080/api/v1/cases?size=1' \
       | python3 -c 'import sys,json;print(json.load(sys.stdin)["content"][0]["caseRef"])')
curl -s -u senior:senior123 "http://localhost:8080/api/v1/cases/$CREF/sar-draft/text"
```

**Why it is senior-only.** A SAR necessarily carries *unmasked* subject PII — one that masks its
subject identifies nobody and is useless to a Financial Intelligence Unit. That makes it the same
class of privileged read as the unmasked customer record (business rule 8), so it carries the
same bar, and generating a draft writes a `SAR_DRAFT_GENERATED` entry to the audit trail.

**Design.** The draft is assembled by `SarDraftService` and written by `SarNarrativeComposer`,
deliberately split: one gathers data, the other writes English. The composer is a pure function
from values to prose — no I/O, no entities — so the wording a compliance team will inevitably
want to revise can be changed and tested without touching a query, a transaction boundary or a
security annotation. It is the same property that makes the detection rules cheap to test.

**It never overstates suspicion.** The narrative quotes each alert's own explanation rather than
paraphrasing, so every sentence is traceable to the detection that produced it. And the
recommended action is derived from the case's actual disposition: a case closed as
`FALSE_POSITIVE` produces a draft that says in terms *"this draft should NOT be filed."*

**Nothing else changed.** The feature is a new package plus one audit-vocabulary constant and one
URL rule. Read-only: it creates no rows and alters no alert, case or disposition.

---

## D. Unit tests (D5) — rules tested at their boundaries

`./gradlew test` → **59 tests, 0 failures.** The boundary *is* the rule, so each condition is
tested from both sides rather than at a convenient midpoint.

| Test class | Cases |
|---|---|
| [`CtrThresholdRuleTest`](../src/test/java/com/meridiantrust/sentinel/detection/rules/CtrThresholdRuleTest.java) | 9,999.99 → no · **10,000.00 → yes** · 10,000.01 → yes · both directions · configurable threshold · dedup key uniqueness · explanation specificity |
| [`StructuringRuleTest`](../src/test/java/com/meridiantrust/sentinel/detection/rules/StructuringRuleTest.java) | 2 → no · **3 → yes** · 25h span → no · band inclusive at 9,000.00 and 9,999.99 · out-of-band excluded · 5 txns → **one** alert not three · retunable `minCount` |
| [`RapidMovementRuleTest`](../src/test/java/com/meridiantrust/sentinel/detection/rules/RapidMovementRuleTest.java) | 79% → no · **80% → yes** · 49h → no · below deposit floor → no · multi-counterparty aggregation · deposit with no outflow → no · outflow *before* deposit → no |
| [`BehavioralDeviationRuleTest`](../src/test/java/com/meridiantrust/sentinel/detection/rules/BehavioralDeviationRuleTest.java) | below multiplier → no · **above → yes** · **cold start → no** · below value floor → no · explanation specificity |
| [`HighRiskJurisdictionRuleTest`](../src/test/java/com/meridiantrust/sentinel/detection/rules/HighRiskJurisdictionRuleTest.java) | **₹1 to a sanctioned country → alert** · unlisted → no · watchlisted counterparty from a clean jurisdiction → yes · higher weight wins · null country safe |
| [`RoundAmountPatternRuleTest`](../src/test/java/com/meridiantrust/sentinel/detection/rules/RoundAmountPatternRuleTest.java) | 3 round → yes · mixed → no · below floor → no |
| [`RiskScoringServiceTest`](../src/test/java/com/meridiantrust/sentinel/alerting/RiskScoringServiceTest.java) | base weight · customer risk + PEP uplift · clamping at 100 · log-scaled magnitude · recurrence cap · **noisy-OR does not saturate** · monotonicity · ordering preserved |
| [`PiiMaskerTest`](../src/test/java/com/meridiantrust/sentinel/common/PiiMaskerTest.java) | names · identifiers · emails · phones · null and short-value edge cases |
| [`SarNarrativeComposerTest`](../src/test/java/com/meridiantrust/sentinel/sar/SarNarrativeComposerTest.java) | narrative essentials · alert explanations quoted verbatim · PEP called out · singular/plural wording · **no unsubstituted format placeholder reaches a regulator** · false positive → "do NOT file" |

---

## E. Non-functional requirements

| Requirement | Status | Evidence |
|---|---|---|
| Java 17+, Spring Boot, Data JPA, Spring Security, Spring Web | ✅ | Java 17 toolchain, Spring Boot 3.5.6 — [`build.gradle`](../build.gradle) |
| Relational DB (PostgreSQL preferred) + migration scripts | ✅ | PostgreSQL 16, Flyway-managed; Hibernate `ddl-auto=validate` so Flyway owns the schema |
| **10,000 transactions < 2 minutes** | ✅ **1,581 ms** | Printed on every startup by `SeedDataLoader`; also returned as `durationMs` on every ingestion response |
| Sub-second streaming evaluation | ✅ | `POST /api/v1/ingestion/transactions` returns `detectionMs` inline |
| Thread-safe, no duplicate or lost alerts | ✅ | `UNIQUE(dedup_key)` + check/insert/catch/merge, each in its own transaction ([`AlertPersister`](../src/main/java/com/meridiantrust/sentinel/alerting/service/AlertPersister.java)); stateless rules over an immutable `RuleContext`; `@Version` optimistic locking |
| No hardcoded secrets | ✅ | Every credential is `${ENV_VAR:default}`; [`.env.example`](../.env.example) committed, `.env` git-ignored; passwords BCrypt-hashed at startup |
| **RBAC enforced at the API layer, not just the UI** | ✅ | URL rules in [`SecurityConfig`](../src/main/java/com/meridiantrust/sentinel/common/security/SecurityConfig.java) **and** `@PreAuthorize` on service methods. Verified: analyst → admin `403`, analyst → unmasked PII `403`, senior → unmasked PII `200`, anonymous → `401` |
| Versioned REST, consistent JSON errors, OpenAPI | ✅ | All paths under `/api/v1`; RFC 7807 `ProblemDetail` from [`GlobalExceptionHandler`](../src/main/java/com/meridiantrust/sentinel/common/error/GlobalExceptionHandler.java) with a `traceId` matching the server logs |
| Immutable audit of all alert/case state transitions | ✅ | [`AuditService`](../src/main/java/com/meridiantrust/sentinel/common/audit/service/AuditService.java) writes in `REQUIRES_NEW` so the trail survives a rolled-back business transaction; `GET /api/v1/audit` |
| Clean layered architecture | ✅ | Every feature: `controller/ · dto/ · service/ · repository/ · model/`. A controller never touches a repository. |
| Meaningful SLF4J logging | ✅ | Correlation id per request via [`CorrelationIdFilter`](../src/main/java/com/meridiantrust/sentinel/common/config/CorrelationIdFilter.java), propagated into detection worker threads |
| No real PII — synthetic data only | ✅ | [`SyntheticDataGenerator`](../src/main/java/com/meridiantrust/sentinel/seed/SyntheticDataGenerator.java), deterministic seed, `@example.test` email domain |
| Data validation; reject malformed, enforce referential integrity, log errors | ✅ | 3-stage chain in [`ingestion/validation/`](../src/main/java/com/meridiantrust/sentinel/ingestion/validation/); rejections persisted to `ingestion_error` **with the raw record** and returned inline |
| Incremental/streaming ingestion alongside bulk | ✅ | `POST /api/v1/ingestion/transactions` (single, synchronous detection) alongside CSV and JSON batch |
| Rules configurable without redeployment | ✅ | `rule_config` table + `PATCH /api/v1/admin/rules/{code}`; validated, cache-invalidated, audited |
| Alert de-duplication / aggregation | ✅ | Deterministic `dedupKey` per *pattern*; repeats fold in and raise `triggerCount` instead of creating new alerts |

---

## F. Not delivered — stated plainly

| Item | Why | Impact |
|---|---|---|
| **API / RBAC integration tests** | Time. | RBAC verified manually (403/401/200, §E). Automating it is the first test to add next. |
| **~~Docker build not executed~~** | — | ✅ **Now verified.** `docker compose up --build` builds and runs the full stack; API reports healthy, seed loads, all six rules fire, RBAC enforced. See [DEMO.md § Running with Docker](DEMO.md#0-start). |

### One judgement call worth your attention

The CTR threshold is set to **₹10,000**. The brief says "$10,000" while business rule 9 mandates
an INR base currency, and ₹10,000 is a low bar for Indian retail banking — which is why
`CTR_THRESHOLD` produces 2,194 of the 2,533 seeded alerts. It is a `rule_config` value, not a
constant, so one `PATCH` retunes it:

```bash
curl -u admin:admin123 -X PATCH localhost:8080/api/v1/admin/rules/CTR_THRESHOLD \
  -H 'Content-Type: application/json' -d '{"params":{"thresholdBase":800000}}'
```

That this is a one-line runtime change rather than a redeployment is itself the demonstration of
the "configurable without code redeployment" requirement.

---

## G. Extension ideas — status of all six

The brief lists six extension ideas. They were explicitly deferred to a second phase; **one is
delivered**. Stated in full so the position is unambiguous rather than inferred:

| # | Extension idea | Status |
|---|---|---|
| 1 | ML-based anomaly scoring (isolation forest / clustering) | ❌ Not attempted. Detection is purely rule-based — which is what the brief asks for first: *"I don't need a perfect AI model."* |
| 2 | Network/graph visualisation of linked accounts and flows | ❌ Not attempted. **Blocked by the data, not the code:** the seed generator writes counterparty accounts as synthetic references, so **0 transactions currently link to another account in the system**. A graph would render empty; delivering this means extending the generator to create genuine account-to-account chains first. |
| 3 | Kafka + Spring Cloud Stream real-time pipeline | ⚠️ **Partial.** Sub-second streaming alerting *is* delivered over REST (`POST /api/v1/ingestion/transactions`, measured at 5 ms), and [`TransactionIngestPort`](../src/main/java/com/meridiantrust/sentinel/ingestion/port/TransactionIngestPort.java) is a real hexagonal seam so a Kafka adapter is additive. But there is **no broker and no Spring Cloud Stream** — the seam is architecture, not the extension. |
| 4 | **Automated SAR draft generation** | ✅ **Delivered** — see [§C3](#c3-extension-delivered--automated-sar-draft-generation). |
| 5 | Analyst productivity dashboard (volume trends, false-positive rate, time-to-disposition) | ⚠️ **Partial.** `/dashboard/stats` and `/dashboard/heatmap` return real aggregates and drive the UI, but the three metrics the brief names specifically are **not** implemented. The data exists (`audit_log` holds every transition with timestamps; `alerts` holds dispositions), so these are queries away — but on a fresh boot almost nothing is disposed, so they would read as zeroes without seeding realistic dispositions first. |
| 6 | Configurable rule versioning / A-B testing of thresholds | ❌ Not attempted. Rule changes *are* runtime-tunable and fully audited with a before/after snapshot (§B, rule config), but there is no versioning, no experiment assignment and no impact measurement. |

**Why #4 was the one chosen.** It had the best ratio of effort to value: every input already
existed — the detection engine's alert explanations are already narrative prose, cases already
group alerts against one customer, and evidence transactions are already linked — so the work was
composition rather than new capability. It also converts `ESCALATED_TO_SAR` from a status string
into an actual business artifact, which is what the brief's *"clean workflow to act on it"* asks
for.
