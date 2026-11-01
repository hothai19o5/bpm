package com.x.bpm.identity.adapter.security;

import com.x.bpm.shared.api.ApiErrorMapping;
import com.x.bpm.shared.api.ApiResponseWriter;
import com.x.bpm.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** JSON cho lỗi Bearer authentication/authorization; không lộ chi tiết token. */
@Component
public class ApiSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ApiResponseWriter writer;

    public ApiSecurityHandlers(ApiResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(@NonNull HttpServletRequest request, HttpServletResponse response,
                         @NonNull AuthenticationException exception) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        writer.writeError(request, response, ErrorCode.UNAUTHENTICATED,
                ApiErrorMapping.messageFor(HttpStatus.UNAUTHORIZED));
    }

    @Override
    public void handle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                       @NonNull AccessDeniedException exception) throws IOException {
        writer.writeError(request, response, ErrorCode.FORBIDDEN,
                ApiErrorMapping.messageFor(HttpStatus.FORBIDDEN));
    }
}
