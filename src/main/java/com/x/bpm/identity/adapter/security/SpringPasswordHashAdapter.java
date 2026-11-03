package com.x.bpm.identity.adapter.security;

import com.x.bpm.identity.application.PasswordHashPort;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Adapter duy nhất bọc PasswordEncoder; domain/application không phụ thuộc Spring Security. */
@Component
public class SpringPasswordHashAdapter implements PasswordHashPort {
    private final PasswordEncoder passwordEncoder;

    public SpringPasswordHashAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(encodedPassword, "encodedPassword");
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
