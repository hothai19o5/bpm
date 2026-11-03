package com.x.bpm.organization.application;

import com.x.bpm.organization.domain.OrganizationStatus;

public record NewPosition(String code, String name, String description,
        OrganizationStatus status) {
}
