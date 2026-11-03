package com.x.bpm.organization.api;

import com.x.bpm.organization.application.NewDepartment;
import com.x.bpm.organization.application.NewPosition;
import com.x.bpm.organization.application.OrganizationManagementUseCase;
import com.x.bpm.organization.domain.Department;
import com.x.bpm.organization.domain.OrganizationStatus;
import com.x.bpm.organization.domain.Position;
import com.x.bpm.shared.api.ApiResponse;
import com.x.bpm.shared.api.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
@PreAuthorize("hasAuthority('organization.manage')")
public class OrganizationAdminController {
    private final OrganizationManagementUseCase organizations;
    private final ApiResponseFactory responses;

    public OrganizationAdminController(OrganizationManagementUseCase organizations,
            ApiResponseFactory responses) {
        this.organizations = organizations;
        this.responses = responses;
    }

    @GetMapping("/departments")
    public ApiResponse<List<Department>> departments(HttpServletRequest request) {
        return responses.success(organizations.departments(), request);
    }

    @PostMapping("/departments")
    public ApiResponse<Department> createDepartment(@RequestBody DepartmentWriteRequest body,
            HttpServletRequest request) {
        requireDepartmentBody(body);
        return responses.success(organizations.createDepartment(new NewDepartment(body.code(),
                body.name(), body.parentId(), body.managerUserId(), body.status())), request);
    }

    @PutMapping("/departments/{id}")
    public ApiResponse<Department> updateDepartment(@PathVariable UUID id,
            @RequestBody DepartmentWriteRequest body, HttpServletRequest request) {
        requireDepartmentBody(body);
        return responses.success(organizations.updateDepartment(id, body.expectedRevision(),
                body.code(), body.name(), body.parentId(), body.managerUserId(), body.status()),
                request);
    }

    @GetMapping("/positions")
    public ApiResponse<List<Position>> positions(HttpServletRequest request) {
        return responses.success(organizations.positions(), request);
    }

    @PostMapping("/positions")
    public ApiResponse<Position> createPosition(@RequestBody PositionWriteRequest body,
            HttpServletRequest request) {
        requirePositionBody(body);
        return responses.success(organizations.createPosition(new NewPosition(body.code(),
                body.name(), body.description(), body.status())), request);
    }

    @PutMapping("/positions/{id}")
    public ApiResponse<Position> updatePosition(@PathVariable UUID id,
            @RequestBody PositionWriteRequest body, HttpServletRequest request) {
        requirePositionBody(body);
        return responses.success(organizations.updatePosition(id, body.expectedRevision(),
                body.code(), body.name(), body.description(), body.status()), request);
    }

    private static void requireDepartmentBody(DepartmentWriteRequest body) {
        if (body == null || body.code() == null || body.code().isBlank()
                || body.name() == null || body.name().isBlank() || body.status() == null
                || body.expectedRevision() < 0 || body.code().length() > 100
                || body.name().length() > 200) {
            throw new com.x.bpm.shared.error.ApplicationException(
                    com.x.bpm.shared.error.ErrorCode.INVALID_REQUEST,
                    "Dữ liệu phòng ban không hợp lệ.");
        }
    }

    private static void requirePositionBody(PositionWriteRequest body) {
        if (body == null || body.code() == null || body.code().isBlank()
                || body.name() == null || body.name().isBlank() || body.status() == null
                || body.expectedRevision() < 0 || body.code().length() > 100
                || body.name().length() > 200) {
            throw new com.x.bpm.shared.error.ApplicationException(
                    com.x.bpm.shared.error.ErrorCode.INVALID_REQUEST,
                    "Dữ liệu chức danh không hợp lệ.");
        }
    }

    public record DepartmentWriteRequest(String code, String name, UUID parentId,
            UUID managerUserId, OrganizationStatus status, long expectedRevision) {
    }

    public record PositionWriteRequest(String code, String name, String description,
            OrganizationStatus status, long expectedRevision) {
    }
}
