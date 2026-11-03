package com.x.bpm.identity.api;

import com.x.bpm.identity.application.AccountSecurityUseCase;
import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.NewUserAccount;
import com.x.bpm.identity.application.PasswordHashPort;
import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.support.IsolatedPostgresTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Vòng đời xác thực đi qua filter chain thật với identity state trong PostgreSQL test. */
@IsolatedPostgresTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private AuthAccountPort accounts;
    @Autowired private PasswordHashPort passwordHashes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private AccountSecurityUseCase accountSecurity;

    @Test
    void loginPersistsOnlyRefreshHashAndMeUsesServerValidatedPrincipal() throws Exception {
        var username = "login-" + UUID.randomUUID();
        var password = "correct horse battery staple";
        var account = accounts.create(new NewUserAccount(username, "Login Test User",
                passwordHashes.hash(password), AccountStatus.ACTIVE, false));

        var login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new AuthController.LoginRequest(
                                username.toUpperCase(), password))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpect(jsonPath("$.data.user.id").value(account.id().toString()))
                .andReturn();
        var body = objectMapper.readTree(login.getResponse().getContentAsString());
        var accessToken = body.get("data").get("accessToken").asString();
        var refreshToken = body.get("data").get("refreshToken").asString();

        var jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isEqualTo(account.id().toString());
        assertThat(jwt.getClaimAsString("sid")).isNotBlank();
        assertThat(((Number) jwt.getClaim("ver")).longValue())
                .isEqualTo(account.securityVersion());
        assertThat(jdbc.queryForObject("select count(*) from login_grants where user_id = ?",
                Long.class, account.id())).isEqualTo(1);
        var storedHash = jdbc.queryForObject("""
                select c.token_hash from refresh_credentials c
                join login_grants g on g.id = c.grant_id
                where g.user_id = ?
                """, String.class, account.id());
        assertThat(storedHash).hasSize(64).isNotEqualTo(refreshToken);

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.id").value(account.id().toString()))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.displayName").value("Login Test User"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(account.passwordHash()))));
    }

    @Test
    void temporaryPasswordCanReadMeButIsBlockedFromBusinessApis() throws Exception {
        var username = "temporary-" + UUID.randomUUID();
        var password = "temporary correct horse staple";
        accounts.create(new NewUserAccount(username, "Temporary User",
                passwordHashes.hash(password), AccountStatus.ACTIVE, true));
        var login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();
        var accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("data").get("accessToken").asString();

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/not-yet-implemented")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(
                        "Bạn phải đổi mật khẩu tạm trước khi tiếp tục."));

        var loginAgain = login(username, password);
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(loginAgain.refreshToken())))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(
                        "Bạn phải đổi mật khẩu tạm trước khi tiếp tục."));
    }

    @Test
    void refreshRotatesOnceAndReplayDurablyRevokesTheWholeGrant() throws Exception {
        var username = "refresh-" + UUID.randomUUID();
        var password = "correct horse battery staple";
        accounts.create(new NewUserAccount(username, "Refresh Test User",
                passwordHashes.hash(password), AccountStatus.ACTIVE, false));
        var first = login(username, password);

        var refreshed = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(first.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();
        var refreshedJson = objectMapper.readTree(refreshed.getResponse().getContentAsString());
        var secondAccess = refreshedJson.get("data").get("accessToken").asString();
        var secondRefresh = refreshedJson.get("data").get("refreshToken").asString();
        var grantId = jwtDecoder.decode(first.accessToken()).getClaimAsString("sid");

        assertThat(secondRefresh).isNotEqualTo(first.refreshToken());
        assertThat(jwtDecoder.decode(secondAccess).getClaimAsString("sid")).isEqualTo(grantId);
        assertThat(jdbc.queryForObject("""
                select count(*) from refresh_credentials
                where grant_id = cast(? as uuid) and consumed_at is not null
                """, Long.class, grantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from refresh_credentials where grant_id = cast(? as uuid)
                """, Long.class, grantId)).isEqualTo(2);
        mvc.perform(get("/api/v1/auth/me").header("Authorization",
                        "Bearer " + first.accessToken()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(first.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(jdbc.queryForObject("""
                select revoke_reason from login_grants where id = cast(? as uuid)
                """, String.class, grantId)).isEqualTo("refresh-token-replay");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + secondAccess))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(secondRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutIsIdempotentAndDoesNotRevokeAnotherLoginGrant() throws Exception {
        var username = "logout-" + UUID.randomUUID();
        var password = "correct horse battery staple";
        accounts.create(new NewUserAccount(username, "Logout Test User",
                passwordHashes.hash(password), AccountStatus.ACTIVE, false));
        var first = login(username, password);
        var second = login(username, password);

        for (var attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(first.refreshToken())))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().doesNotExist("Set-Cookie"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        mvc.perform(get("/api/v1/auth/me").header("Authorization",
                        "Bearer " + first.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",
                        "Bearer " + second.accessToken()))
                .andExpect(status().isOk());
        var firstGrantId = jwtDecoder.decode(first.accessToken()).getClaimAsString("sid");
        var secondGrantId = jwtDecoder.decode(second.accessToken()).getClaimAsString("sid");
        assertThat(jdbc.queryForObject("""
                select revoke_reason from login_grants where id = cast(? as uuid)
                """, String.class, firstGrantId)).isEqualTo("logout");
        assertThat(jdbc.queryForObject("""
                select count(*) from login_grants
                where id = cast(? as uuid) and revoked_at is null
                """, Long.class, secondGrantId)).isEqualTo(1);
    }

    @Test
    void twoConcurrentRefreshRequestsCannotBothRotateTheSameCredential() throws Exception {
        var username = "concurrent-refresh-" + UUID.randomUUID();
        var password = "correct horse battery staple";
        accounts.create(new NewUserAccount(username, "Concurrent Refresh User",
                passwordHashes.hash(password), AccountStatus.ACTIVE, false));
        var tokens = login(username, password);
        var requestBody = refreshBody(tokens.refreshToken());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var calls = List.of(
                    executor.submit(() -> performRefreshAfter(start, requestBody)),
                    executor.submit(() -> performRefreshAfter(start, requestBody)));
            start.countDown();
            var statuses = List.of(calls.get(0).get(), calls.get(1).get());

            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        } finally {
            executor.shutdownNow();
        }

        var grantId = jwtDecoder.decode(tokens.accessToken()).getClaimAsString("sid");
        assertThat(jdbc.queryForObject("""
                select count(*) from refresh_credentials where grant_id = cast(? as uuid)
                """, Long.class, grantId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select revoke_reason from login_grants where id = cast(? as uuid)
                """, String.class, grantId)).isEqualTo("refresh-token-replay");
    }

    @Test
    void loginFailureDoesNotRevealWhetherUsernamePasswordOrStatusWasWrong() throws Exception {
        var username = "inactive-" + UUID.randomUUID();
        var password = "correct horse battery staple";
        accounts.create(new NewUserAccount(username, "Inactive User",
                passwordHashes.hash(password), AccountStatus.INACTIVE, false));

        for (var request : java.util.List.of(
                new AuthController.LoginRequest("missing-user", password),
                new AuthController.LoginRequest(username, "wrong-password"),
                new AuthController.LoginRequest(username, password))) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.message").value(
                            "Thông tin đăng nhập không hợp lệ."));
        }
    }

    @Test
    void structurallyInvalidLoginRequestReturnsInvalidRequestWithoutEchoingPassword() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"secret-marker\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-marker"))));
    }

    @Test
    void invalidRefreshRequestIsRejectedWithoutEchoingTheToken() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new AuthController.RefreshTokenRequest(
                                "unknown-secret-marker"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("unknown-secret-marker"))));
    }

    @Test
    void temporaryPasswordCanBeChangedAndEveryOldTokenIsRevoked() throws Exception {
        var username = "change-password-" + UUID.randomUUID();
        var currentPassword = "temporary horse battery staple";
        var newPassword = "replacement horse battery staple";
        var account = accounts.create(new NewUserAccount(username, "Password Change User",
                passwordHashes.hash(currentPassword), AccountStatus.ACTIVE, true));
        var oldTokens = login(username, currentPassword);
        var oldVersion = ((Number) jwtDecoder.decode(oldTokens.accessToken())
                .getClaim("ver")).longValue();

        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + oldTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.ChangePasswordRequest(currentPassword,
                                        newPassword))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + oldTokens.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldTokens.refreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.LoginRequest(username, currentPassword))))
                .andExpect(status().isUnauthorized());

        var newTokens = login(username, newPassword);
        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + newTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
        assertThat(jdbc.queryForObject("select security_version from users where id = ?",
                Long.class, account.id())).isEqualTo(oldVersion + 1);
        assertThat(jdbc.queryForObject("""
                select count(*) from login_grants
                where user_id = ? and revoke_reason = 'password-changed'
                """, Long.class, account.id())).isEqualTo(1);
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordAndUnsafeReplacementWithoutLeakingThem()
            throws Exception {
        var username = "change-password-errors-" + UUID.randomUUID();
        var currentPassword = "correct horse battery staple";
        accounts.create(new NewUserAccount(username, "Password Error User",
                passwordHashes.hash(currentPassword), AccountStatus.ACTIVE, false));
        var tokens = login(username, currentPassword);

        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.ChangePasswordRequest("wrong-secret-marker",
                                        "replacement horse battery staple"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("wrong-secret-marker"))));

        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.ChangePasswordRequest(currentPassword,
                                        "unsafe-marker"))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("newPassword"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("unsafe-marker"))));

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void deactivationAndReactivationNeverRestoreTokensIssuedBeforeTheLock() throws Exception {
        var username = "account-lock-" + UUID.randomUUID();
        var password = "correct horse battery staple";
        var account = accounts.create(new NewUserAccount(username, "Account Lock User",
                passwordHashes.hash(password), AccountStatus.ACTIVE, false));
        var oldTokens = login(username, password);

        accountSecurity.deactivate(account.id());
        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + oldTokens.accessToken()))
                .andExpect(status().isUnauthorized());
        accountSecurity.reactivate(account.id());

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + oldTokens.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldTokens.refreshToken())))
                .andExpect(status().isUnauthorized());
        var newTokens = login(username, password);
        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + newTokens.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetRevokesAllGrantsAndRequiresTheReplacementPasswordToBeChanged()
            throws Exception {
        var username = "password-reset-" + UUID.randomUUID();
        var oldPassword = "correct horse battery staple";
        var temporaryPassword = "temporary replacement password";
        var account = accounts.create(new NewUserAccount(username, "Password Reset User",
                passwordHashes.hash(oldPassword), AccountStatus.ACTIVE, false));
        var first = login(username, oldPassword);
        var second = login(username, oldPassword);

        accountSecurity.resetPassword(account.id(), temporaryPassword);

        for (var tokens : List.of(first, second)) {
            mvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + tokens.accessToken()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(tokens.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.LoginRequest(username, oldPassword))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.LoginRequest(username, temporaryPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
        assertThat(jdbc.queryForObject("""
                select count(*) from login_grants
                where user_id = ? and revoke_reason = 'password-reset'
                """, Long.class, account.id())).isEqualTo(2);
    }

    @Test
    void concurrentRefreshAndPasswordChangeCannotLeaveAUsableOldGrant() throws Exception {
        var username = "refresh-change-race-" + UUID.randomUUID();
        var currentPassword = "correct horse battery staple";
        var newPassword = "replacement horse battery staple";
        accounts.create(new NewUserAccount(username, "Refresh Change Race User",
                passwordHashes.hash(currentPassword), AccountStatus.ACTIVE, false));
        var oldTokens = login(username, currentPassword);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        RefreshAttempt refreshAttempt;
        try {
            var refreshCall = executor.submit(() -> performRefreshAttemptAfter(start,
                    refreshBody(oldTokens.refreshToken())));
            var passwordCall = executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/auth/change-password")
                                .header("Authorization", "Bearer " + oldTokens.accessToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new AuthController.ChangePasswordRequest(currentPassword,
                                                newPassword))))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            refreshAttempt = refreshCall.get();
            assertThat(passwordCall.get()).isEqualTo(200);
            assertThat(refreshAttempt.status()).isIn(200, 401);
        } finally {
            executor.shutdownNow();
        }

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + oldTokens.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(oldTokens.refreshToken())))
                .andExpect(status().isUnauthorized());
        if (refreshAttempt.status() == 200) {
            var data = objectMapper.readTree(refreshAttempt.body()).get("data");
            mvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer "
                                    + data.get("accessToken").asString()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(data.get("refreshToken").asString())))
                    .andExpect(status().isUnauthorized());
        }
    }

    private Tokens login(String username, String password) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        var data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return new Tokens(data.get("accessToken").asString(),
                data.get("refreshToken").asString());
    }

    private byte[] refreshBody(String refreshToken) throws Exception {
        return objectMapper.writeValueAsBytes(new AuthController.RefreshTokenRequest(refreshToken));
    }

    private int performRefreshAfter(CountDownLatch start, byte[] requestBody) throws Exception {
        start.await();
        return mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andReturn().getResponse().getStatus();
    }

    private RefreshAttempt performRefreshAttemptAfter(CountDownLatch start, byte[] requestBody)
            throws Exception {
        start.await();
        var response = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andReturn().getResponse();
        return new RefreshAttempt(response.getStatus(), response.getContentAsString());
    }

    private record Tokens(String accessToken, String refreshToken) {
    }

    private record RefreshAttempt(int status, String body) {
    }
}
