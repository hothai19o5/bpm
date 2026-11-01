package com.x.bpm.shared.api;

import com.x.bpm.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Ghi JSON ở filter/security, nơi @RestControllerAdvice không bắt được lỗi. */
@Component
public class ApiResponseWriter {
    private final ObjectMapper objectMapper;
    private final ApiResponseFactory responses;

    public ApiResponseWriter(ObjectMapper objectMapper, ApiResponseFactory responses) {
        this.objectMapper = objectMapper;
        this.responses = responses;
    }

    public void writeError(HttpServletRequest request, HttpServletResponse response,
                           ErrorCode code, String safeMessage) throws IOException {
        var status = ApiErrorMapping.statusFor(code);
        write(response, status, responses.error(code, safeMessage, List.of(), status, request));
    }

    public void writeSuccess(HttpServletRequest request, HttpServletResponse response,
                             Object data, String message) throws IOException {
        write(response, HttpStatus.OK, responses.success(data, HttpStatus.OK, message, request));
    }

    private void write(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        // Serialize first: errors do not leave a partially serialized JSON body.
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
