package com.x.bpm.process.application;

import java.time.Instant;
import java.util.UUID;

/** versionNumber thuộc nghiệp vụ, độc lập với engineVersion và revision. */
public record ProcessVersionSummary(UUID id, UUID processDefinitionId, int versionNumber,
        Status status, UUID createdBy, UUID publishedBy, Instant publishedAt,
        Instant updatedAt, long revision) {
    public enum Status { DRAFT, PUBLISHED, RETIRED }
}
