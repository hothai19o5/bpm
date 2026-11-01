package com.x.bpm.shared.error;

import java.util.Objects;

/**
 * Lỗi an toàn để hiển thị; field hoặc elementId có thể null tùy loại lỗi.
 * Không mang rejectedValue, exception gốc hoặc payload nhạy cảm.
 */
public record ValidationIssue(String field, String message, String elementId) {
    public ValidationIssue {
        Objects.requireNonNull(message, "message");
    }
}
