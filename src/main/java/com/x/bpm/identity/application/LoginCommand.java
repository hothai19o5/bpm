package com.x.bpm.identity.application;

/** Input thuần Java của login use case; không để lộ password qua toString. */
public record LoginCommand(String username, String password) {
    public LoginCommand {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
    }

    @Override
    public String toString() {
        return "LoginCommand[username=" + username + ", password=<redacted>]";
    }
}
