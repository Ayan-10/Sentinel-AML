package com.meridiantrust.sentinel.detection.model;


/**
 * Declares the data a rule needs, so {@code RuleWindowLoader} can fetch exactly
 * those windows once per chunk rather than per transaction.
 *
 * <p>This is the mechanism behind the performance target: a rule states its
 * data shape, and the loader batches accordingly. Without it, every rule would
 * query independently and the bulk path would be O(rules x transactions) round
 * trips.
 */
public enum RuleScope {

    /** Evaluates each transaction independently — no history required. */
    TRANSACTION,

    /** Needs a time-ordered window of the account's recent transactions. */
    ACCOUNT_WINDOW,

    /** Needs the customer's historical daily volumes (behavioural baseline). */
    CUSTOMER_WINDOW
}
