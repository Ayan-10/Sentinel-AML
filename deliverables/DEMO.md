# Sentinel AML — Demo Walkthrough

**Deliverable D7:** the full **ingestion → detection → alert → case disposition** flow.

Every command below was executed against the running system and the output shown is real, not
illustrative. Copy-paste them in order.

---

## 0. Start

```bash
docker compose up --build          # or, locally:
./gradlew bootRun --args='--spring.profiles.active=local'
```

Wait for `SENTINEL SEED COMPLETE` in the log. Then set:

```bash
B=http://localhost:8080/api/v1
```

> **zsh users:** don't put `-u user:pass` in a variable — zsh won't word-split it and curl will
> send no credentials (a silent `401`). Type the flag inline, as below.

---

## 1. Ingestion — what the seed actually did

The seed loads through the **real ingestion path** (validation → FX normalisation → detection),
not straight into the database, so the startup log is a genuine measurement:

```
Seeded 500 customers and 737 accounts
Planted typology 'Structuring / smurfing'              → CUST_00026 / ACC_000042
Planted typology 'Rapid movement / layering'           → CUST_00078 / ACC_000118
Planted typology 'High-risk / sanctioned jurisdiction' → CUST_00134 / ACC_000204
Planted typology 'Behavioural deviation'               → CUST_00210 / ACC_000312
Planted typology 'Repeated round amounts'              → CUST_00267 / ACC_000389
Planted typology 'CTR threshold (incl. post-FX breach)'→ CUST_00314 / ACC_000456
Bulk detection complete: 10000 transactions, 5 chunks, 2533 hits, 2533 new alerts in 608 ms

  Transactions    : 10000 accepted, 0 rejected
  Alerts created  : 2533
  Detection time  : 1581 ms   (NFR target: 10,000 txns < 120,000 ms)
```

Confirm from the API:

```bash
curl -s -u analyst:analyst123 "$B/dashboard/stats" | python3 -m json.tool
```

```
Alerts: 2533   Transactions: 10000   Customers: 500
  BEHAVIORAL_DEVIATION   = 326
  CTR_THRESHOLD          = 2194
  HIGH_RISK_JURISDICTION = 7
  RAPID_MOVEMENT         = 3
  ROUND_AMOUNT_PATTERN   = 2
  STRUCTURING            = 1
```

All six rules firing. (`CTR_THRESHOLD` dominates because the threshold is ₹10,000 — see
[DELIVERABLES.md §F](DELIVERABLES.md#one-judgement-call-worth-your-attention).)

---

## 2. Detection → the alert queue

Highest risk first, **PII masked** (business rules 7 and 8):

```bash
curl -s -u analyst:analyst123 \
  "$B/alerts?size=5&sortBy=riskScore&direction=desc" | python3 -m json.tool
```

```json
{
  "alertRef": "ALT-20260919-3C8CCA1C",
  "customerId": "CUST_00134",
  "customerNameMasked": "K****** N***",
  "accountMasked": "******0204",
  "ruleCode": "HIGH_RISK_JURISDICTION",
  "riskScore": 77,
  "severity": "HIGH",
  "status": "OPEN",
  "evidenceAmountBase": 850.0
}
```

**Look at that top alert: ₹850.** The highest-scoring item in a 10,000-transaction dataset is a
tiny transfer — because business rule 4 applies **no amount threshold**, and a small "test"
transfer is exactly how an operator verifies a channel before moving real money.

---

## 3. Alert detail — *why* it fired

```bash
REF=$(curl -s -u analyst:analyst123 \
  "$B/alerts?customerId=CUST_00134&ruleCode=HIGH_RISK_JURISDICTION&sortBy=riskScore&direction=desc&size=1" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['content'][0]['alertRef'])")

curl -s -u analyst:analyst123 "$B/alerts/$REF" | python3 -m json.tool
```

```
score 77 HIGH

explanation:
  "Transaction TXN_SANCTION_01 (INR 850.00, 16 Sep 2026 14:49) on account ACC_000204
   involves Iran, listed as SANCTIONED (source: FATF Call for Action). Counterparty
   'Delta Bridge Exchange' appears on the SANCTIONS watchlist. Business rule 4 requires
   an alert regardless of transaction value."

scoreBreakdown:
  {ruleWeight: 30, jurisdictionUplift: 40, customerRiskUplift: 7,
   pepUplift: 0, magnitudeUplift: 0, recurrenceUplift: 0, total: 77}

evidence: ["TXN_SANCTION_01"]
```

The explanation is **generated from the evidence** — real amounts, real counterparty, real
source — not a canned string with the rule name in it. The score is broken down too, so an
analyst can interrogate the number rather than just trust it.

Try the other five typologies:

```bash
curl -s -u analyst:analyst123 "$B/alerts?customerId=CUST_00026&ruleCode=STRUCTURING"
curl -s -u analyst:analyst123 "$B/alerts?customerId=CUST_00078&ruleCode=RAPID_MOVEMENT"
curl -s -u analyst:analyst123 "$B/alerts?customerId=CUST_00210&ruleCode=BEHAVIORAL_DEVIATION"
curl -s -u analyst:analyst123 "$B/alerts?customerId=CUST_00267&ruleCode=ROUND_AMOUNT_PATTERN"
curl -s -u analyst:analyst123 "$B/alerts?customerId=CUST_00314&ruleCode=CTR_THRESHOLD"
```

The structuring explanation, for example:

> "5 transactions totalling INR 47,500.00 were made on account ACC_000042 within 24 hours
> (18 Sep 2026 09:15 to 18 Sep 2026 21:15). Each fell between INR 9,000.00 and INR 9,999.99 —
> individually below the reporting threshold, consistent with deliberate structuring to avoid it."

---

## 4. Streaming detection — sub-second, alerts returned inline

> **Timestamps are UTC.** The container's JVM runs in UTC, and ingestion rejects
> future-dated transactions. If your local clock is ahead of UTC (IST is +5:30), a
> hand-written local time will be refused with
> `BUSINESS_RULE: txnTimestamp '...' is in the future`. Generate the value instead:

```bash
TS=$(python3 -c "import datetime;print((datetime.datetime.utcnow()
      - datetime.timedelta(minutes=10)).strftime('%Y-%m-%dT%H:%M:%S'))")

curl -s -u admin:admin123 -X POST "$B/ingestion/transactions" \
  -H 'Content-Type: application/json' \
  -d "{\"transactionId\":\"TXN_DEMO_1\",\"accountId\":\"ACC_000204\",
       \"txnTimestamp\":\"$TS\",\"direction\":\"DEBIT\",
       \"amount\":950.00,\"currency\":\"INR\",
       \"counterpartyName\":\"Delta Bridge Exchange\",\"counterpartyCountry\":\"IR\"}"
```

```json
{"transactionId":"TXN_DEMO_1","accepted":true,"rulesTriggered":1,
 "alertsCreated":1,"detectionMs":5}
```

The caller learns **within the same HTTP request** that the transaction alerted.

Validation rejects malformed input with a reason, per record:

```bash
curl -s -u admin:admin123 -X POST "$B/ingestion/transactions" \
  -H 'Content-Type: application/json' \
  -d '{"transactionId":"BAD_1","accountId":"ACC_NOPE","txnTimestamp":"2026-09-19T10:00:00",
       "direction":"DEBIT","amount":100,"currency":"XYZ"}'
```
→ `400` with an RFC 7807 body naming the exact field and why.

---

## 5. Alert → Case

```bash
CREF=$(curl -s -u analyst:analyst123 -X POST "$B/cases" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"CUST_00134\",
       \"title\":\"Sanctioned counterparty exposure\",
       \"alertRefs\":[\"$REF\"],
       \"narrative\":\"Repeated transfers to a designated entity.\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['caseRef'])")

echo $CREF        # CASE-20260919-1F5E98
```

The case opens as `NEW` with priority derived from the noisy-OR aggregate of its member alerts,
and the alert moves to `IN_REVIEW` automatically.

---

## 6. Disposition — and RBAC that actually bites

An **analyst** tries to escalate to SAR:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -u analyst:analyst123 \
  -X PATCH "$B/cases/$CREF/status" -H 'Content-Type: application/json' \
  -d '{"targetStatus":"CLOSED","disposition":"ESCALATED_TO_SAR","reason":"x"}'
```
```
403
```

SAR filing has regulatory consequences, so the authorisation requirement is a property of the
**disposition itself**, not a check an endpoint might forget.

A **senior analyst** can:

```bash
curl -s -u senior:senior123 -X PATCH "$B/cases/$CREF/status" \
  -H 'Content-Type: application/json' \
  -d '{"targetStatus":"CLOSED","disposition":"ESCALATED_TO_SAR",
       "reason":"Confirmed exposure to a designated entity. SAR filed with the FIU."}'
```
```
CASE-20260919-1F5E98  CLOSED  ESCALATED_TO_SAR
```

Closing the case **cascades** onto every member alert:

```bash
curl -s -u analyst:analyst123 "$B/alerts/$REF"
```
```
status CLOSED | disposition ESCALATED_TO_SAR | disposedBy senior
```

**Business rule 6 in action:** the alert still exists, still queryable, carrying its disposition,
its reason and the identity of the analyst who made the call. Nothing was deleted.

---

## 7. The immutable audit trail

```bash
curl -s -u analyst:analyst123 "$B/audit?entityType=CASE&entityId=$CREF"
curl -s -u analyst:analyst123 "$B/audit?entityType=ALERT&entityId=$REF"
```

```
CASE
  CASE_STATUS_CHANGED    senior   NEW -> CLOSED   Disposition: ESCALATED_TO_SAR — Confirmed...
  CASE_DISPOSED          senior                   Disposition ESCALATED_TO_SAR by senior — ...
  CASE_OPENED            analyst      -> NEW      Opened over 1 alert(s), aggregate score 77

ALERT
  ALERT_STATUS_CHANGED   senior   IN_REVIEW -> CLOSED   Closed via case CASE-20260919-1F5E98
  ALERT_LINKED_TO_CASE   analyst                        Linked to case CASE-20260919-1F5E98
  ALERT_CREATED          SYSTEM       -> OPEN           Rule HIGH_RISK_JURISDICTION fired, score 77
```

A complete attribution chain: the **engine** raised it, an **analyst** investigated it, a
**senior analyst** disposed of it. There is no API to write or delete these rows — they are a
side effect of the transitions themselves.

---

## 8. Rule tuning with no redeployment

Disable a rule:

```bash
curl -s -u admin:admin123 -X PATCH "$B/admin/rules/STRUCTURING" \
  -H 'Content-Type: application/json' -d '{"enabled":false}'
```
```
STRUCTURING  enabled = false  updatedBy = admin
```

Retune its thresholds — catch pairs instead of triples:

```bash
curl -s -u admin:admin123 -X PATCH "$B/admin/rules/STRUCTURING" \
  -H 'Content-Type: application/json' \
  -d '{"enabled":true,"params":{"minCount":2,"windowHours":24,
                                "lowerBound":9000,"upperBound":9999.99}}'
```

**Effective on the next evaluation. No restart, no redeployment.**

A configuration that could never fire is rejected rather than silently disabling a control:

```bash
curl -s -u admin:admin123 -X PATCH "$B/admin/rules/STRUCTURING" \
  -H 'Content-Type: application/json' -d '{"params":{"lowerBound":9999,"upperBound":9000}}'
```
```
400 — lowerBound (9999.0) must be less than upperBound (9000.0)
      — an inverted band matches nothing
```

And the change itself is audited with a full before/after snapshot:

```bash
curl -s -u analyst:analyst123 "$B/audit?entityType=RULE&entityId=STRUCTURING"
```
```
RULE_UPDATED | admin | Changed by admin | before: enabled=true,weight=35,... | after: ...
```

Restore the default before moving on:

```bash
curl -s -u admin:admin123 -X PATCH "$B/admin/rules/STRUCTURING" \
  -H 'Content-Type: application/json' \
  -d '{"enabled":true,"params":{"minCount":3,"windowHours":24,
                                "lowerBound":9000,"upperBound":9999.99}}'
```

---

## 9. PII masking across roles (business rule 8)

```bash
curl -s -u analyst:analyst123 "$B/customers/CUST_00134"        # masked
curl -s -u analyst:analyst123 "$B/customers/CUST_00134/full"   # 403
curl -s -u senior:senior123   "$B/customers/CUST_00134/full"   # 200, unmasked
curl -s                        "$B/alerts"                      # 401
```

| Call | Result |
|---|---|
| analyst → masked customer | `200` — `K****** N***`, `*******4821` |
| analyst → **full** customer | **`403`** |
| senior → full customer | `200` — full name, full national ID |
| unauthenticated → alerts | **`401`** |

Enforced at the API layer by **both** a URL rule and a `@PreAuthorize` on the service method —
the UI never participates in the decision.

---

## Summary of the flow

```
CSV / REST / stream
   ↓  validate (3-stage chain, per-record rejection with reasons)
   ↓  normalise to base currency (business rule 9)
   ↓  persist
DETECTION  6 rules, windows pre-loaded per chunk, evaluated in parallel
   ↓  RuleHit
SCORING    weighted 0-100 + uplifts, breakdown retained
   ↓  dedup on UNIQUE(dedup_key) — repeats fold in, no alert floods
ALERT      explainable, evidence-linked, queued by risk
   ↓  analyst opens a case
CASE       investigate → dispose (reason + identity mandatory)
   ↓
AUDIT      every transition, immutable, attributed
```
