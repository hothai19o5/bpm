package com.x.bpm.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsLongPassphrasesWithoutCompositionRules() {
        assertThat(policy.violations("correct horse battery staple", "employee")).isEmpty();
    }

    @Test
    void countsUnicodeCodePointsInsteadOfUtf16Units() {
        var twelveEmoji = "😀".repeat(12);
        assertThat(twelveEmoji.length()).isEqualTo(24);
        assertThat(policy.violations(twelveEmoji, "employee")).isEmpty();
    }

    @Test
    void rejectsUnsafeOrAccountDerivedPasswords() {
        assertThat(policy.violations("short", "employee"))
                .contains(PasswordPolicy.Violation.TOO_SHORT);
        assertThat(policy.violations("Employee", " employee "))
                .contains(PasswordPolicy.Violation.TOO_SHORT,
                        PasswordPolicy.Violation.MATCHES_USERNAME);
        assertThat(policy.violations("            ", "employee"))
                .contains(PasswordPolicy.Violation.ONLY_WHITESPACE);
        assertThat(policy.violations("long-enough" + Character.toString(0) + "password", "employee"))
                .contains(PasswordPolicy.Violation.CONTROL_CHARACTER);
    }
}
