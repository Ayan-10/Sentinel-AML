package com.meridiantrust.sentinel.common.security;


import org.springframework.stereotype.Component;

/**
 * Business rule 8: sensitive PII is masked in list views and revealed in full
 * only to authorised roles in detail views.
 *
 * <p>Masking lives in one injectable, unit-tested component rather than being
 * re-implemented per endpoint. The alternative — a boolean flag on a shared DTO
 * — fails open: forget to set it and PII ships. Here the list DTO is built from
 * masked values and <em>has no unmasked field to leak</em>, so a serialisation
 * mistake cannot expose what was never mapped.
 */
@Component
public class PiiMasker {

    private static final String MASK_CHAR = "*";

    /** {@code Krishna Sharma} → {@code K****** S*****} */
    public String maskName(String fullName) {
        if (isBlank(fullName)) {
            return fullName;
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(maskWord(parts[i]));
        }
        return out.toString();
    }

    /** Keeps the last 4 characters — enough for an analyst to correlate, not to identify. */
    public String maskIdentifier(String value) {
        if (isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return MASK_CHAR.repeat(trimmed.length());
        }
        return MASK_CHAR.repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }

    /** {@code krishna.sharma@gmail.com} → {@code k*************@g****.com} */
    public String maskEmail(String email) {
        if (isBlank(email) || !email.contains("@")) {
            return maskIdentifier(email);
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String domainName = dot > 0 ? domain.substring(0, dot) : domain;
        String tld = dot > 0 ? domain.substring(dot) : "";
        return maskWord(local) + "@" + maskWord(domainName) + tld;
    }

    /** Keeps the country prefix and last 4 digits: {@code +91-XXXXXX2955}. */
    public String maskPhone(String phone) {
        if (isBlank(phone)) {
            return phone;
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 4) {
            return MASK_CHAR.repeat(trimmed.length());
        }
        int prefixEnd = trimmed.indexOf('-');
        String prefix = prefixEnd > 0 ? trimmed.substring(0, prefixEnd + 1) : "";
        String rest = trimmed.substring(prefix.length());
        if (rest.length() <= 4) {
            return prefix + MASK_CHAR.repeat(rest.length());
        }
        return prefix + MASK_CHAR.repeat(rest.length() - 4) + rest.substring(rest.length() - 4);
    }

    /** Account numbers: {@code ACC_000123} → {@code ******0123} */
    public String maskAccount(String accountId) {
        return maskIdentifier(accountId);
    }

    private String maskWord(String word) {
        if (word.isEmpty()) {
            return word;
        }
        if (word.length() == 1) {
            return word;
        }
        return word.charAt(0) + MASK_CHAR.repeat(word.length() - 1);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
