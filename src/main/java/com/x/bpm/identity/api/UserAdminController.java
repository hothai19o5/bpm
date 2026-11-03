package com.x.bpm.identity.api;

import com.x.bpm.identity.application.CreateManagedUserCommand;
import com.x.bpm.identity.application.ManagedUserSummary;
import com.x.bpm.identity.application.UserAdministrationUseCase;
import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.shared.api.ApiResponse;
import com.x.bpm.shared.api.ApiResponseFactory;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('identity.users.manage')")
public class UserAdminController {
    private final UserAdministrationUseCase users;
    private final ApiResponseFactory responses;

    public UserAdminController(UserAdministrationUseCase users, ApiResponseFactory responses) {
        this.users = users;
        this.responses = responses;
    }

    @GetMapping("/users")
    public ApiResponse<List<ManagedUserSummary>> users(HttpServletRequest request) {
        return responses.success(users.users(), request);
    }

    @GetMapping("/users/{id}")
    public ApiResponse<ManagedUserSummary> user(@PathVariable UUID id,
            HttpServletRequest request) {
        return responses.success(users.user(id), request);
    }

    @PostMapping("/users")
    public ApiResponse<ManagedUserSummary> create(@RequestBody CreateUserRequest body,
            HttpServletRequest request) {
        requireCreateBody(body);
        return responses.success(users.create(new CreateManagedUserCommand(body.username(),
                body.displayName(), body.temporaryPassword(), body.status(), body.departmentId(),
                body.positionId(), body.roleIds())), request);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('identity.users.manage') and hasAuthority('organization.manage')")
    public ApiResponse<ManagedUserSummary> update(@PathVariable UUID id,
            @RequestBody UpdateUserRequest body, HttpServletRequest request) {
        if (body == null || body.displayName() == null || body.displayName().isBlank()
                || body.expectedRevision() < 0) {
            throw invalid();
        }
        return responses.success(users.update(id, body.expectedRevision(), body.displayName(),
                body.departmentId(), body.positionId(), safeRoles(body.roleIds())), request);
    }

    @PostMapping("/users/{id}/deactivate")
    public ApiResponse<Void> deactivate(@PathVariable UUID id, HttpServletRequest request) {
        users.deactivate(id);
        return responses.success(null, request);
    }

    @PostMapping("/users/{id}/reactivate")
    public ApiResponse<Void> reactivate(@PathVariable UUID id, HttpServletRequest request) {
        users.reactivate(id);
        return responses.success(null, request);
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable UUID id,
            @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        if (body == null || body.temporaryPassword() == null
                || body.temporaryPassword().isBlank()) {
            throw invalid();
        }
        users.resetPassword(id, body.temporaryPassword());
        return responses.success(null, request);
    }

    private static void requireCreateBody(CreateUserRequest body) {
        if (body == null || body.username() == null || body.username().isBlank()
                || body.displayName() == null || body.displayName().isBlank()
                || body.temporaryPassword() == null || body.temporaryPassword().isBlank()
                || body.status() == null) {
            throw invalid();
        }
    }

    private static Set<UUID> safeRoles(Set<UUID> roleIds) {
        return roleIds == null ? Set.of() : Set.copyOf(roleIds);
    }

    private static ApplicationException invalid() {
        return new ApplicationException(ErrorCode.INVALID_REQUEST,
                "Dữ liệu tài khoản không hợp lệ.");
    }

    public record CreateUserRequest(String username, String displayName,
            String temporaryPassword, AccountStatus status, UUID departmentId, UUID positionId,
            Set<UUID> roleIds) {
        @Override
        public String toString() {
            return "CreateUserRequest[username=" + username
                    + ", temporaryPassword=<redacted>]";
        }
    }

    public record UpdateUserRequest(String displayName, UUID departmentId, UUID positionId,
            Set<UUID> roleIds, long expectedRevision) {
    }

    public record ResetPasswordRequest(String temporaryPassword) {
        @Override
        public String toString() {
            return "ResetPasswordRequest[temporaryPassword=<redacted>]";
        }
    }
}
