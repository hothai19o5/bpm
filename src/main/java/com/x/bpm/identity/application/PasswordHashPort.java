package com.x.bpm.identity.application;

/** Che giấu PasswordEncoder khỏi domain/application. */
public interface PasswordHashPort {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
