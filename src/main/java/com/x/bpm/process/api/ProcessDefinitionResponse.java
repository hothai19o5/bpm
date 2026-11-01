package com.x.bpm.process.api;

import com.x.bpm.process.application.ProcessDefinitionSummary;
import java.time.Instant;
import java.util.Objects;

/** DTO công khai; danh sách chỉ lộ metadata tối thiểu đã qua scope query. */
public record ProcessDefinitionResponse(String id, String key, String name,
                                         long revision, Instant updatedAt) {

    public static ProcessDefinitionResponse from(ProcessDefinitionSummary summary) {
        Objects.requireNonNull(summary, "summary must not be null");

        return new ProcessDefinitionResponse(
                summary.id(),
                summary.key(),
                summary.name(),
                summary.revision(),
                summary.updatedAt()
        );
    }
}
