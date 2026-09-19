package com.meridiantrust.sentinel.reference.model;

/** A hit against the jurisdiction or counterparty watchlist (business rule 4). */
public record JurisdictionMatch(String matchType, String matchedValue, String category,
                                int riskWeight, String source) {

    public static final String TYPE_COUNTRY = "COUNTRY";
    public static final String TYPE_COUNTERPARTY = "COUNTERPARTY";
}
