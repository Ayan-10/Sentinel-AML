package com.meridiantrust.sentinel.common;

import com.meridiantrust.sentinel.common.security.PiiMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business rule 8: PII masking.
 *
 * <p>Masking must never throw. A null or unusually short value is a data-quality
 * problem, not a reason to fail a list request — and an exception here would
 * take down the whole alert queue.
 */
class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    @DisplayName("names keep only initials")
    void masksNames() {
        assertThat(masker.maskName("Krishna Sharma")).isEqualTo("K****** S*****");
        assertThat(masker.maskName("Anika Rose Fernandes")).isEqualTo("A**** R*** F********");
    }

    @Test
    @DisplayName("identifiers keep the last four characters for correlation")
    void masksIdentifiers() {
        assertThat(masker.maskIdentifier("IDN12344821")).isEqualTo("*******4821");
        assertThat(masker.maskIdentifier("ACC_000123")).isEqualTo("******0123");
    }

    @Test
    @DisplayName("emails keep the first letter and the TLD")
    void masksEmail() {
        assertThat(masker.maskEmail("krishna.sharma@gmail.com")).isEqualTo("k*************@g****.com");
    }

    @Test
    @DisplayName("phone numbers keep the country prefix and last four digits")
    void masksPhone() {
        assertThat(masker.maskPhone("+91-6939042955")).isEqualTo("+91-******2955");
    }

    @Test
    @DisplayName("null and short values are handled without throwing")
    void handlesEdgeCases() {
        assertThat(masker.maskName(null)).isNull();
        assertThat(masker.maskIdentifier(null)).isNull();
        assertThat(masker.maskIdentifier("AB")).isEqualTo("**");
        assertThat(masker.maskEmail("not-an-email")).isEqualTo("********mail");
        assertThat(masker.maskPhone("12")).isEqualTo("**");
        assertThat(masker.maskName("")).isEmpty();
    }
}
