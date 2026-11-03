package com.x.bpm;

import com.x.bpm.identity.adapter.security.ApiSecurityHandlers;
import com.x.bpm.identity.adapter.security.CurrentAccountJwtAuthenticationConverter;
import com.x.bpm.identity.adapter.security.NimbusAccessTokenIssuer;
import com.x.bpm.identity.adapter.security.TemporaryPasswordAuthorizationManager;
import com.x.bpm.identity.application.AccessTokenIssuer;
import com.x.bpm.identity.application.AuthenticatedAccount;
import com.x.bpm.identity.application.ResolveAuthenticatedActorUseCase;
import com.x.bpm.identity.config.JwtSecurityConfiguration;
import com.x.bpm.identity.config.JwtSecurityProperties;
import com.x.bpm.identity.config.SecurityConfiguration;
import com.x.bpm.shared.adapter.web.CorrelationIdFilter;
import com.x.bpm.shared.api.ApiErrorController;
import com.x.bpm.shared.api.ApiExceptionHandler;
import com.x.bpm.shared.api.ApiResponse;
import com.x.bpm.shared.api.ApiResponseFactory;
import com.x.bpm.shared.api.ApiResponseWriter;
import com.x.bpm.shared.config.ApiFoundationConfiguration;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actual signed JWTs go through the real decoder; probe endpoints exist only in test source. */
@WebMvcTest(controllers = ApiErrorController.class, properties = {
        "bpm.security.cors.allowed-origins=http://localhost:5173",
        "bpm.security.jwt.secret-key=test-only-bpm-jwt-secret-key-not-used-in-dev-2026"
})
@Import({SecurityConfiguration.class, JwtSecurityConfiguration.class, NimbusAccessTokenIssuer.class,
        CurrentAccountJwtAuthenticationConverter.class, TemporaryPasswordAuthorizationManager.class,
        ApiSecurityHandlers.class, ApiFoundationConfiguration.class, ApiResponseFactory.class,
        ApiResponseWriter.class, ApiExceptionHandler.class, ApiFoundationWebTests.ProbeController.class})
class ApiFoundationWebTests {
    private static final UUID ALLOWED_USER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID DENIED_USER = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID LOGIN_GRANT = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccessTokenIssuer tokenIssuer;
    @Autowired private JwtEncoder encoder;
    @Autowired private JwtSecurityProperties jwtProperties;
    @MockitoBean private ResolveAuthenticatedActorUseCase identityResolver;

    @BeforeEach
    void resolveCurrentIdentityFromServerState() {
        when(identityResolver.resolve(anyString(), anyString(), anyLong())).thenAnswer(invocation -> {
            var userId = UUID.fromString(invocation.getArgument(0, String.class));
            var grantId = UUID.fromString(invocation.getArgument(1, String.class));
            var version = invocation.getArgument(2, Long.class);
            var permissions = userId.equals(ALLOWED_USER) ? Set.of("PROCESS_READ") : Set.<String>of();
            return new AuthenticatedAccount(userId, "test-user", "Test User", permissions,
                    false, grantId, version);
        });
    }

    @Test
    void unauthenticatedApiReturnsJsonBearerChallengeAndCorrelationId() throws Exception {
        mvc.perform(get("/api/v1/test/probe").header("X-Correlation-ID", "manual-p13-001")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("X-Correlation-ID", "manual-p13-001"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").value("manual-p13-001"));
    }

    @Test
    void allowedPreflightSupportsAuthorizationWithoutCookies() throws Exception {
        mvc.perform(options("/api/v1/test/probe").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void unknownCorsOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/test/probe").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void issuedTokenAllowsPostWithoutCsrfAndDoesNotCreateSession() throws Exception {
        var token = tokenIssuer.issue(ALLOWED_USER, LOGIN_GRANT, 0, Set.of("PROCESS_READ"));
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(jwtProperties.accessTokenTtl().toSeconds());
        assertThat(token.toString()).doesNotContain(token.accessToken());
        var result = mvc.perform(post("/api/v1/test/probe")
                        .header("Authorization", "Bearer " + token.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Valid\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Valid"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(get("/api/v1/test/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void oldSessionAuthenticationAndQueryParameterTokensAreNotAccepted() throws Exception {
        var session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "old-user", "unused-test-value", List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        mvc.perform(get("/api/v1/test/probe").session(session)).andExpect(status().isUnauthorized());
        var token = tokenIssuer.issue(ALLOWED_USER, LOGIN_GRANT, 0, Set.of()).accessToken();
        mvc.perform(get("/api/v1/test/probe").param("access_token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedExpiredNotYetValidAndInvalidClaimsAreRejected() throws Exception {
        var expired = sign(encoder, claims -> claims.issuedAt(Instant.now().minusSeconds(900))
                .notBefore(Instant.now().minusSeconds(900)).expiresAt(Instant.now().minusSeconds(300)));
        var future = sign(encoder, claims -> claims.notBefore(Instant.now().plusSeconds(300)));
        var wrongIssuer = sign(encoder, claims -> claims.issuer("https://wrong.example"));
        var wrongAudience = sign(encoder, claims -> claims.audience(List.of("another-api")));
        var missingExpiry = sign(encoder, claims -> claims.claims(values -> values.remove("exp")));
        var badPermissions = sign(encoder, claims -> claims.claim("permissions", List.of(123)));
        var badSid = sign(encoder, claims -> claims.claim("sid", "not-a-uuid"));
        var badVersion = sign(encoder, claims -> claims.claim("ver", -1));
        for (var token : List.of("broken.jwt.value", expired, future, wrongIssuer, wrongAudience,
                missingExpiry, badPermissions, badSid, badVersion)) {
            mvc.perform(get("/api/v1/test/probe").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(content().string(not(containsString(token))));
        }
    }

    @Test
    void wrongSignatureIsRejected() throws Exception {
        var otherKey = new SecretKeySpec(
                "other-test-signing-key-not-valid-for-bpm-2026".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        var otherEncoder = NimbusJwtEncoder.withSecretKey(otherKey).algorithm(MacAlgorithm.HS256).build();
        mvc.perform(get("/api/v1/test/probe")
                        .header("Authorization", "Bearer " + sign(otherEncoder, claims -> { })))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void verifiedPermissionsControlMethodAccess() throws Exception {
        var allowed = tokenIssuer.issue(ALLOWED_USER, LOGIN_GRANT, 0, Set.of()).accessToken();
        var denied = tokenIssuer.issue(DENIED_USER, LOGIN_GRANT, 0,
                Set.of("PROCESS_READ")).accessToken();
        mvc.perform(get("/api/v1/test/scoped").header("Authorization", "Bearer " + allowed))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/test/scoped").header("Authorization", "Bearer " + denied))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidBeanReturns422WithoutRejectedPayload() throws Exception {
        mvc.perform(post("/api/v1/test/probe").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].rejectedValue").doesNotExist());
    }

    @Test
    void invalidPaginationReturns400InsteadOf500() throws Exception {
        for (var parameter : Map.of("page", "-1", "size", "101", "sort", "name,invalid").entrySet()) {
            mvc.perform(get("/api/v1/test/pagination").header("Authorization", bearer())
                            .param(parameter.getKey(), parameter.getValue()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    void malformedJsonAndUnsupportedMethodKeepTheirHttpSemantics() throws Exception {
        mvc.perform(post("/api/v1/test/probe").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/test/conflict").header("Authorization", bearer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void businessConflictAndUnexpectedExceptionHaveSafeBodies() throws Exception {
        mvc.perform(get("/api/v1/test/conflict").header("Authorization", bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));
        mvc.perform(get("/api/v1/test/failure").header("Authorization", bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("internal-test-marker"))));
    }

    @Test
    void deniedBusinessAccessReturns403InsteadOf500() throws Exception {
        mvc.perform(get("/api/v1/test/forbidden").header("Authorization", bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidCorrelationIdIsReplacedAndPreviousMdcIsRestored() throws Exception {
        var previous = MDC.get(CorrelationIdFilter.MDC_KEY);
        MDC.put(CorrelationIdFilter.MDC_KEY, "outer-scope");
        try {
            var result = mvc.perform(get("/api/v1/test/probe").header("X-Correlation-ID", "bad id"))
                    .andExpect(status().isUnauthorized()).andReturn();
            var actualId = result.getResponse().getHeader("X-Correlation-ID");
            assertThat(actualId).matches("[A-Za-z0-9._-]{1,64}").isNotEqualTo("bad id");
            var body = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("correlationId").asString()).isEqualTo(actualId);
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("outer-scope");
        } finally {
            if (previous == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previous);
            }
        }
    }

    private String bearer() {
        return "Bearer " + tokenIssuer.issue(ALLOWED_USER, LOGIN_GRANT, 0,
                Set.of("PROCESS_READ")).accessToken();
    }

    private String sign(JwtEncoder signer, Consumer<JwtClaimsSet.Builder> customize) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(jwtProperties.issuer())
                .subject(ALLOWED_USER.toString())
                .audience(List.of(jwtProperties.audience())).issuedAt(now).notBefore(now)
                .expiresAt(now.plusSeconds(1800)).id(UUID.randomUUID().toString())
                .claim("sid", LOGIN_GRANT.toString()).claim("ver", 0L)
                .claim("permissions", List.of("PROCESS_READ"));
        customize.accept(claims);
        return signer.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims.build())).getTokenValue();
    }

    @RestController
    static class ProbeController {
        private final ApiResponseFactory responses;

        ProbeController(ApiResponseFactory responses) {
            this.responses = responses;
        }

        @GetMapping("/api/v1/test/probe")
        public ApiResponse<Map<String, String>> getProbe(HttpServletRequest request) {
            return responses.success(Map.of("name", "probe"), request);
        }

        @PostMapping("/api/v1/test/probe")
        public ApiResponse<Map<String, String>> postProbe(@Valid @RequestBody ProbeRequest body,
                                                         HttpServletRequest request) {
            return responses.success(Map.of("name", body.name()), request);
        }

        @GetMapping("/api/v1/test/pagination")
        public ApiResponse<PageQuery> pagination(@RequestParam(name = "page", required = false) Integer page,
                @RequestParam(name = "size", required = false) Integer size,
                @RequestParam(name = "sort", required = false) String sort, HttpServletRequest request) {
            return responses.success(PageQuery.from(page, size, sort), request);
        }

        @PreAuthorize("hasAuthority('PROCESS_READ')")
        @GetMapping("/api/v1/test/scoped")
        public ApiResponse<Map<String, String>> scoped(HttpServletRequest request) {
            return responses.success(Map.of("name", "scoped"), request);
        }

        @GetMapping("/api/v1/test/conflict")
        public void conflict() {
            throw new ApplicationException(ErrorCode.REVISION_CONFLICT, "Dữ liệu đã thay đổi.");
        }

        @GetMapping("/api/v1/test/failure")
        public void failure() {
            throw new IllegalStateException("internal-test-marker");
        }

        @GetMapping("/api/v1/test/forbidden")
        public void forbidden() {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed");
        }
    }

    record ProbeRequest(@NotBlank String name) {
    }
}
