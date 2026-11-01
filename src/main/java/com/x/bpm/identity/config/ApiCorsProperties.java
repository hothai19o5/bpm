package com.x.bpm.identity.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Chỉ chấp nhận origin cụ thể; API dùng Authorization header, không cookie credentials. */
@ConfigurationProperties(prefix = "bpm.security.cors")
public record ApiCorsProperties(List<String> allowedOrigins) {
    public ApiCorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        for (var origin : allowedOrigins) {
            var uri = URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || origin.contains("*")) {
                throw new IllegalArgumentException("CORS origins must be exact HTTP(S) origins without paths or wildcards");
            }
        }
    }
}
