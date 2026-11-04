package com.x.bpm.form.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Version nghiệp vụ và revision khóa lạc quan là hai giá trị độc lập. */
public record FormVersion(UUID id, UUID formDefinitionId, int versionNumber, Status status,
        Map<String, Object> schema, Map<String, Object> uiSchema, UUID createdBy,
        UUID publishedBy, Instant publishedAt, long revision) {
    public FormVersion {
        schema = JsonDocument.snapshot(schema);
        uiSchema = JsonDocument.snapshot(uiSchema);
    }

    public enum Status { DRAFT, PUBLISHED, RETIRED }
}
