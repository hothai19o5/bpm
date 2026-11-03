package com.x.bpm.identity.api;

import com.x.bpm.identity.application.ManagedPermission;
import com.x.bpm.identity.application.ManagedRoleDetails;
import com.x.bpm.identity.application.NewManagedRole;
import com.x.bpm.identity.application.RoleAdministrationUseCase;
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
@PreAuthorize("hasAuthority('identity.roles.manage')")
public class RoleAdminController {
    private final RoleAdministrationUseCase roles;
    private final ApiResponseFactory responses;

    public RoleAdminController(RoleAdministrationUseCase roles, ApiResponseFactory responses) {
        this.roles = roles;
        this.responses = responses;
    }

    @GetMapping("/permissions")
    public ApiResponse<List<ManagedPermission>> permissions(HttpServletRequest request) {
        return responses.success(roles.permissions(), request);
    }

    @GetMapping("/roles")
    public ApiResponse<List<ManagedRoleDetails>> roles(HttpServletRequest request) {
        return responses.success(roles.roles(), request);
    }

    @PostMapping("/roles")
    public ApiResponse<ManagedRoleDetails> create(@RequestBody RoleWriteRequest body,
            HttpServletRequest request) {
        requireBody(body);
        return responses.success(roles.create(body.toCommand()), request);
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<ManagedRoleDetails> update(@PathVariable UUID id,
            @RequestBody RoleWriteRequest body, HttpServletRequest request) {
        requireBody(body);
        return responses.success(roles.update(id, body.expectedRevision(), body.toCommand()),
                request);
    }

    private static void requireBody(RoleWriteRequest body) {
        if (body == null || body.code() == null || body.code().isBlank()
                || body.name() == null || body.name().isBlank()
                || body.expectedRevision() < 0) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,
                    "Dữ liệu role không hợp lệ.");
        }
    }

    public record RoleWriteRequest(String code, String name, String description, boolean active,
            Set<UUID> permissionIds, long expectedRevision) {
        NewManagedRole toCommand() {
            return new NewManagedRole(code, name, description, active, permissionIds);
        }
    }
}
