package com.x.bpm.process.domain;

import java.util.UUID;

/** Danh mục nghiệp vụ; owner không tự cấp quyền thiết kế/publish/task. */
public record ProcessDefinition(UUID id, String key, String name, String description,
        UUID ownerUserId, Status status, UUID activeProcessVersionId, long revision) {
    public enum Status { ACTIVE, INACTIVE }
}
