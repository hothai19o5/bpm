package com.x.bpm.identity.application;

/** Dữ liệu đổi mật khẩu; không để lộ credential khi log command. */
public record ChangePasswordCommand(String currentPassword, String newPassword) {
    @Override
    public String toString() {
        return "ChangePasswordCommand[currentPassword=<redacted>, newPassword=<redacted>]";
    }
}
