package com.x.bpm.identity.api;

import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.NewUserAccount;
import com.x.bpm.identity.application.PasswordHashPort;
import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.support.IsolatedPostgresTest;
import java.util.UUID;
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

/** Login và /me đi qua filter chain thật với identity state trong PostgreSQL test. */
@IsolatedPostgresTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private AuthAccountPort accounts;
    @Autowired private PasswordHashPort passwordHashes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtDecoder jwtDecoder;

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
}
