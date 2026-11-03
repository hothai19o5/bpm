package com.x.bpm.identity.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Chính sách mật khẩu P2.1a, không phụ thuộc PasswordEncoder. */
public final class PasswordPolicy {
    public static final int MIN_CODE_POINTS = 12;
    public static final int MAX_CODE_POINTS = 128;

    public Set<Violation> violations(String password, String username) {
        var violations = EnumSet.noneOf(Violation.class);
        if (password == null) {
            violations.add(Violation.MISSING);
            return Collections.unmodifiableSet(violations);
        }
        var length = password.codePointCount(0, password.length());
        if (length < MIN_CODE_POINTS) {
            violations.add(Violation.TOO_SHORT);
        }
        if (length > MAX_CODE_POINTS) {
            violations.add(Violation.TOO_LONG);
        }
        if (password.codePoints().anyMatch(Character::isISOControl)) {
            violations.add(Violation.CONTROL_CHARACTER);
        }
        if (password.codePoints().allMatch(Character::isWhitespace)) {
            violations.add(Violation.ONLY_WHITESPACE);
        }
        if (username != null && !username.isBlank()
                && password.strip().toLowerCase(Locale.ROOT).equals(UserAccount.normalizeUsername(username))) {
            violations.add(Violation.MATCHES_USERNAME);
        }
        return Collections.unmodifiableSet(violations);
    }

    public enum Violation {
        MISSING,
        TOO_SHORT,
        TOO_LONG,
        CONTROL_CHARACTER,
        ONLY_WHITESPACE,
        MATCHES_USERNAME
    }
}
