package com.x.bpm.identity.application;

/** Dữ liệu refresh đi vào application; không để token lộ qua toString. */
public record RefreshTokenCommand(String refreshToken) {
    public RefreshTokenCommand {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }
    }

    @Override
    public String toString() {
        return "RefreshTokenCommand[refreshToken=<redacted>]";
    }
}
