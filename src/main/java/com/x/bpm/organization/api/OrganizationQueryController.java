package com.x.bpm.organization.api;

import com.x.bpm.organization.application.ManagerResolution;
import com.x.bpm.organization.application.OrganizationManagementUseCase;
import com.x.bpm.shared.api.ApiResponse;
import com.x.bpm.shared.api.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationQueryController {
    private final OrganizationManagementUseCase organizations;
    private final ApiResponseFactory responses;

    public OrganizationQueryController(OrganizationManagementUseCase organizations,
            ApiResponseFactory responses) {
        this.organizations = organizations;
        this.responses = responses;
    }

    @GetMapping("/users/{userId}/requester-manager")
    public ApiResponse<ManagerResolution> requesterManager(@PathVariable UUID userId,
            HttpServletRequest request) {
        return responses.success(organizations.resolveRequesterManager(userId), request);
    }
}
