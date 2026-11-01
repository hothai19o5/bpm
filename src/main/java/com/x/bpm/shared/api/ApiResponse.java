package com.x.bpm.shared.api;

import com.x.bpm.shared.error.ValidationIssue;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Response chung cho JSON API; HTTP status vẫn là nguồn quyết định thành công/lỗi. */
public record ApiResponse<T>(Instant timestamp, int status, String code, String message,
                              String correlationId, T data, List<ValidationIssue> details) {
    public ApiResponse {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(correlationId, "correlationId");
        if (status < 200 || status > 599) {
            throw new IllegalArgumentException("status must be in 200..599");
        }
        details = List.copyOf(Objects.requireNonNull(details, "details"));
    }
}
