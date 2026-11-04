package com.x.bpm.form.domain;

import java.util.UUID;

public record FormDefinition(UUID id, String key, String name, String description,
        UUID ownerUserId, Status status, long revision) {
    public enum Status { ACTIVE, INACTIVE }
}
