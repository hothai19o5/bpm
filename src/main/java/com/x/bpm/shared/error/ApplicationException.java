package com.x.bpm.shared.error;

import java.util.List;
import java.util.Objects;

/** Lỗi có chủ đích từ use case/policy; safeMessage phải an toàn để trả client. */
public class ApplicationException extends RuntimeException {

    private final ErrorCode code;
    private final List<ValidationIssue> details;

    public ApplicationException(ErrorCode code, String safeMessage,
                                List<ValidationIssue> details) {
        super(safeMessage);
        this.code = Objects.requireNonNull(code, "code");
        if (safeMessage == null || safeMessage.isBlank()) {
            throw new IllegalArgumentException("safeMessage must not be blank");
        }
        this.details = List.copyOf(Objects.requireNonNull(details, "details"));
    }

    public ApplicationException(ErrorCode code, String safeMessage) {
        this(code, safeMessage, List.of());
    }

    public ErrorCode code() {
        return code;
    }

    public List<ValidationIssue> details() {
        return details;
    }
}
