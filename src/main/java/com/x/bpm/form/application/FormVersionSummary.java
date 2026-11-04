package com.x.bpm.form.application;

import com.x.bpm.form.domain.FormVersion;
import java.time.Instant;
import java.util.UUID;

/** Lịch sử không tải schema/UI schema của tất cả phiên bản. */
public record FormVersionSummary(UUID id, int versionNumber, FormVersion.Status status,
        UUID createdBy, UUID publishedBy, Instant publishedAt, long revision) {}
