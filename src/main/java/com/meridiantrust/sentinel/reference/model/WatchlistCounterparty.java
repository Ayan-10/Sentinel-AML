package com.meridiantrust.sentinel.reference.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Business rule 4: named counterparty watchlist (sanctions, shell companies,
 * adverse media).
 *
 * <p>{@code normalizedName} exists because counterparty names arrive from core
 * banking with inconsistent punctuation, spacing and case — "Zenith Holdings
 * FZE", "ZENITH HOLDINGS  F.Z.E." and "zenith holdings fze" are the same
 * entity. Matching on a normalised form is the difference between catching a
 * designated party and missing them on a stray full stop.
 */
@Entity
@Table(name = "watchlist_counterparties")
@Getter
@Setter
@NoArgsConstructor
public class WatchlistCounterparty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    /** SANCTIONS | SHELL_COMPANY | ADVERSE_MEDIA */
    @Column(name = "list_type", nullable = false, length = 32)
    private String listType;

    @Column(length = 2)
    private String country;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    /** Strips everything but alphanumerics and upper-cases. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
