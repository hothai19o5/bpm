package com.x.bpm.shared.api;

import com.x.bpm.shared.adapter.web.CorrelationIdFilter;
import com.x.bpm.shared.error.ErrorCode;
import com.x.bpm.shared.error.ValidationIssue;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

/** Tạo metadata tại biên HTTP; use case/domain không phụ thuộc response này. */
@Component
public class ApiResponseFactory {
    private final Clock clock;

    public ApiResponseFactory(Clock clock) {
        this.clock = clock;
    }

    public <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return success(data, HttpStatus.OK, "Thành công.", request);
    }

    public <T> ApiResponse<T> success(T data, HttpStatus status, String message,
                                     HttpServletRequest request) {
        if (!status.is2xxSuccessful() || status == HttpStatus.NO_CONTENT
                || status == HttpStatus.RESET_CONTENT) {
            throw new IllegalArgumentException("A JSON success response requires a body-capable 2xx status");
        }
        return new ApiResponse<>(Instant.now(clock), status.value(), "OK", message,
                CorrelationIdFilter.getOrCreate(request), data, List.of());
    }

    public ApiResponse<Void> error(ErrorCode code, String safeMessage,
            List<ValidationIssue> details, HttpStatusCode status, HttpServletRequest request) {
        if (!status.isError()) {
            throw new IllegalArgumentException("An error response requires a 4xx or 5xx status");
        }
        return new ApiResponse<>(Instant.now(clock), status.value(), code.name(), safeMessage,
                CorrelationIdFilter.getOrCreate(request), null, details);
    }
}
