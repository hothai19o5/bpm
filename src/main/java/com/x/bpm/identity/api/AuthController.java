package com.x.bpm.identity.api;

import com.x.bpm.identity.application.LoginCommand;
import com.x.bpm.identity.application.LoginResult;
import com.x.bpm.identity.application.LoginUseCase;
import com.x.bpm.identity.application.MeUseCase;
import com.x.bpm.shared.api.ApiResponse;
import com.x.bpm.shared.api.ApiResponseFactory;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter mỏng cho login và danh tính hiện tại. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final LoginUseCase login;
    private final MeUseCase me;
    private final ApiResponseFactory responses;

    public AuthController(LoginUseCase login, MeUseCase me, ApiResponseFactory responses) {
        this.login = login;
        this.me = me;
        this.responses = responses;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@RequestBody LoginRequest body,
            HttpServletRequest request) {
        if (body == null || body.username() == null || body.username().isBlank()
                || body.password() == null || body.password().isBlank()
                || body.username().codePointCount(0, body.username().length()) > 100
                || body.password().codePointCount(0, body.password().length()) > 128) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,
                    "Username hoặc password không hợp lệ.");
        }
        var result = login.login(new LoginCommand(body.username(), body.password()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(responses.success(AuthTokenResponse.from(result), request));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(HttpServletRequest request) {
        var result = me.get();
        var body = new MeResponse(result.id(), result.username(), result.displayName(),
                result.permissions(), result.mustChangePassword());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(responses.success(body, request));
    }

    public record LoginRequest(String username, String password) {
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=<redacted>]";
        }
    }

    public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn,
            Instant expiresAt, String refreshToken, Instant refreshExpiresAt,
            boolean mustChangePassword, UserResponse user) {
        static AuthTokenResponse from(LoginResult result) {
            var access = result.accessToken();
            var user = result.user();
            return new AuthTokenResponse(access.accessToken(), access.tokenType(),
                    access.expiresIn(), access.expiresAt(), result.refreshToken(),
                    result.refreshExpiresAt(), result.mustChangePassword(),
                    new UserResponse(user.id(), user.username(), user.displayName(),
                            user.permissions()));
        }

        @Override
        public String toString() {
            return "AuthTokenResponse[accessToken=<redacted>, tokenType=" + tokenType
                    + ", expiresIn=" + expiresIn + ", expiresAt=" + expiresAt
                    + ", refreshToken=<redacted>, refreshExpiresAt=" + refreshExpiresAt
                    + ", mustChangePassword=" + mustChangePassword + ", user=" + user + "]";
        }
    }

    public record UserResponse(UUID id, String username, String displayName,
            Set<String> permissions) {
        public UserResponse {
            permissions = Set.copyOf(permissions);
        }
    }

    public record MeResponse(UUID id, String username, String displayName,
            Set<String> permissions, boolean mustChangePassword) {
        public MeResponse {
            permissions = Set.copyOf(permissions);
        }
    }
}
