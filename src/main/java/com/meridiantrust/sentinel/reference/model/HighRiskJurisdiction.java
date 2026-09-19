package com.meridiantrust.sentinel.reference.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Business rule 4: the configurable high-risk / sanctions jurisdiction list.
 *
 * <p>Modelled as a table, not an enum. A sanctions list changes by regulatory
 * announcement, sometimes overnight; compiling one into a build artifact would
 * mean a redeployment to respond to a designation.
 */
@Entity
@Table(name = "high_risk_jurisdictions")
@Getter
@Setter
@NoArgsConstructor
public class HighRiskJurisdiction {

    @Id
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "country_name", nullable = false)
    private String countryName;

    /** SANCTIONED | HIGH_RISK | MONITORED — drives the risk-score uplift. */
    @Column(nullable = false, length = 24)
    private String category;

    @Column(name = "risk_weight", nullable = false)
    private int riskWeight = 20;

    private String source;

    @Column(nullable = false)
    private boolean active = true;
}
