package com.x.bpm.organization.application;

import com.x.bpm.organization.domain.OrganizationStatus;
import java.util.UUID;

public record NewDepartment(String code, String name, UUID parentId, UUID managerUserId,
        OrganizationStatus status) {
}
