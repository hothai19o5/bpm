package com.x.bpm.shared.adapter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gắn ID tra cứu request vào response, request attribute và MDC.
 * Đăng ký một lần bằng ApiFoundationConfiguration.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String ATTRIBUTE_NAME = "bpm.correlationId";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var correlationId = getOrCreate(request);
        response.setHeader(HEADER_NAME, correlationId);
        var previousId = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, correlationId);
        long startedAt = System.nanoTime();
        boolean completed = false;
        try {
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                if (completed) {
                    log.info("HTTP method={} path={} status={} elapsedMs={}", request.getMethod(),
                            request.getRequestURI(), response.getStatus(), elapsedMs);
                } else {
                    log.warn("HTTP method={} path={} outcome=unhandled_exception elapsedMs={}",
                            request.getMethod(), request.getRequestURI(), elapsedMs);
                }
            } finally {
                if (previousId == null) {
                    MDC.remove(MDC_KEY);
                } else {
                    MDC.put(MDC_KEY, previousId);
                }
            }
        }
    }

    public static String getOrCreate(HttpServletRequest request) {
        var stored = request.getAttribute(ATTRIBUTE_NAME);
        if (stored instanceof String id && isValidCorrelationId(id)) {
            return id;
        }
        var candidate = request.getHeader(HEADER_NAME);
        var id = isValidCorrelationId(candidate) ? candidate : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE_NAME, id);
        return id;
    }

    private static boolean isValidCorrelationId(String candidate) {
        return candidate != null && VALID_ID.matcher(candidate).matches();
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
