package com.x.bpm.form.application;

import java.util.Map;
import java.util.UUID;

/** Lưu cấu hình đúng draft version; revision bắt buộc để tránh ghi đè. */
public record SaveFormDraft(UUID versionId, Long expectedRevision,
        Map<String, Object> schema, Map<String, Object> uiSchema) { }
