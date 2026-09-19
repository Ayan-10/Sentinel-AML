-- ===========================================================================
-- Reference data: rule configuration, FX table, sanctions/high-risk lists.
-- Everything here is runtime-mutable through the admin API - these are only
-- the bootstrap defaults that encode the stated business rules.
-- ===========================================================================

-- Business rule 9: exchange-rate table, base currency INR.
INSERT INTO fx_rates (currency, rate_to_base) VALUES
  ('INR', 1.00000000),
  ('USD', 83.20000000),
  ('EUR', 90.50000000),
  ('GBP', 105.80000000),
  ('AED', 22.65000000),
  ('SGD', 61.90000000),
  ('CHF', 94.10000000),
  ('HKD', 10.65000000),
  ('JPY', 0.55000000),
  ('RUB', 0.92000000);

-- Business rule 4: configurable high-risk / sanctions jurisdiction list.
INSERT INTO high_risk_jurisdictions (country_code, country_name, category, risk_weight, source) VALUES
  ('IR', 'Iran',                    'SANCTIONED', 40, 'FATF Call for Action'),
  ('KP', 'North Korea',             'SANCTIONED', 40, 'FATF Call for Action'),
  ('SY', 'Syria',                   'SANCTIONED', 40, 'UN Sanctions'),
  ('MM', 'Myanmar',                 'HIGH_RISK',  30, 'FATF Grey List'),
  ('AF', 'Afghanistan',             'HIGH_RISK',  30, 'FATF Grey List'),
  ('YE', 'Yemen',                   'HIGH_RISK',  30, 'FATF Grey List'),
  ('RU', 'Russia',                  'HIGH_RISK',  30, 'Enhanced Due Diligence'),
  ('PK', 'Pakistan',                'HIGH_RISK',  25, 'FATF Monitoring'),
  ('NG', 'Nigeria',                 'HIGH_RISK',  25, 'FATF Grey List'),
  ('PA', 'Panama',                  'HIGH_RISK',  25, 'Offshore Secrecy'),
  ('KY', 'Cayman Islands',          'MONITORED',  20, 'Offshore Secrecy'),
  ('VG', 'British Virgin Islands',  'MONITORED',  20, 'Offshore Secrecy'),
  ('SC', 'Seychelles',              'MONITORED',  20, 'Offshore Secrecy'),
  ('BZ', 'Belize',                  'MONITORED',  20, 'Offshore Secrecy'),
  ('CY', 'Cyprus',                  'MONITORED',  15, 'Enhanced Due Diligence'),
  ('AE', 'United Arab Emirates',    'MONITORED',  15, 'Trade-Based ML Risk');

-- Business rule 4: named counterparty watchlist (shell-company typology).
INSERT INTO watchlist_counterparties (name, normalized_name, list_type, country, notes) VALUES
  ('Zenith Holdings FZE',        'ZENITHHOLDINGSFZE',      'SHELL_COMPANY', 'AE', 'Opaque ownership, trade-based ML indicators'),
  ('Orion Trade Partners Ltd',   'ORIONTRADEPARTNERSLTD',  'SHELL_COMPANY', 'VG', 'Nominee directors, no operating footprint'),
  ('Delta Bridge Exchange',      'DELTABRIDGEEXCHANGE',    'SANCTIONS',     'IR', 'Designated entity - unlicensed money services'),
  ('Pyramid Capital SA',         'PYRAMIDCAPITALSA',       'SHELL_COMPANY', 'PA', 'Layering conduit identified in prior SAR'),
  ('Kestrel Logistics OOO',      'KESTRELLOGISTICSOOO',    'SANCTIONS',     'RU', 'Sanctioned ownership chain'),
  ('Silverline Commodities Ltd', 'SILVERLINECOMMODITIESLTD','ADVERSE_MEDIA','KY', 'Adverse media - invoice fraud allegations');

-- ---------------------------------------------------------------------------
-- Detection rules. `weight` feeds the 0-100 risk score (business rule 7);
-- `params` carries the thresholds and time windows an analyst can retune
-- through PATCH /api/v1/admin/rules/{ruleCode} without a redeploy.
-- ---------------------------------------------------------------------------
INSERT INTO rule_config (rule_code, name, typology, enabled, weight, severity, params, description) VALUES
  ('CTR_THRESHOLD',
   'Currency Transaction Report Threshold',
   'THRESHOLD_BREACH', TRUE, 25, 'MEDIUM',
   '{"thresholdBase":10000}',
   'Business rule 1: any single transaction at or above the reporting threshold (base currency) is flagged for review.'),

  ('STRUCTURING',
   'Structuring / Smurfing',
   'STRUCTURING', TRUE, 35, 'HIGH',
   '{"minCount":3,"windowHours":24,"lowerBound":9000,"upperBound":9999.99}',
   'Business rule 2: three or more transactions on one account inside a 24h window that each sit just below the reporting threshold.'),

  ('RAPID_MOVEMENT',
   'Rapid Movement of Funds (Layering)',
   'LAYERING', TRUE, 35, 'HIGH',
   '{"windowHours":48,"outflowRatio":0.80,"minDepositBase":50000}',
   'Business rule 3: a deposit where 80% or more of the value leaves the account within 48 hours - classic layering.'),

  ('HIGH_RISK_JURISDICTION',
   'High-Risk Jurisdiction / Sanctioned Counterparty',
   'JURISDICTION_RISK', TRUE, 30, 'HIGH',
   '{"alertRegardlessOfAmount":true}',
   'Business rule 4: any transaction touching a listed jurisdiction or watchlisted counterparty alerts regardless of amount.'),

  ('BEHAVIORAL_DEVIATION',
   'Unusual Volume / Behavioural Deviation',
   'BEHAVIOURAL_ANOMALY', TRUE, 25, 'MEDIUM',
   '{"multiplier":3.0,"baselineDays":90,"minBaselineTxns":5,"minDailyBase":25000}',
   'Business rule 5: a customer daily value exceeding 3x their 90-day rolling average.'),

  ('ROUND_AMOUNT_PATTERN',
   'Repeated Round-Number Amounts',
   'ROUND_AMOUNT', TRUE, 15, 'LOW',
   '{"minCount":3,"windowHours":24,"roundingUnit":10000,"minAmountBase":50000}',
   'Repeated suspiciously round amounts on one account inside a short window - an indicator of manufactured rather than organic activity.');
