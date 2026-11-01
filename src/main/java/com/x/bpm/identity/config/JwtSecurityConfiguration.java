package com.x.bpm.identity.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Phát/kiểm tra JWT bằng Nimbus của Spring Security, khóa thuật toán HS256. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtSecurityConfiguration {

    @Bean
    public JwtEncoder jwtEncoder(JwtSecurityProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtSecurityProperties properties, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(properties.issuer()), requiredClaims(properties)));
        return decoder;
    }

    private SecretKeySpec secretKey(JwtSecurityProperties properties) {
        return new SecretKeySpec(properties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private OAuth2TokenValidator<Jwt> requiredClaims(JwtSecurityProperties properties) {
        return token -> {
            var permissions = token.getClaim("permissions");
            boolean validPermissions = permissions instanceof Collection<?> values
                    && values.stream().allMatch(value -> value instanceof String text && !text.isBlank());
            boolean valid = token.getSubject() != null && !token.getSubject().isBlank()
                    && token.getAudience() != null && token.getAudience().contains(properties.audience())
                    && token.getIssuedAt() != null && token.getExpiresAt() != null
                    && token.getExpiresAt().isAfter(token.getIssuedAt())
                    && token.getId() != null && !token.getId().isBlank() && validPermissions;
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required JWT claims are invalid", null));
        };
    }
}
