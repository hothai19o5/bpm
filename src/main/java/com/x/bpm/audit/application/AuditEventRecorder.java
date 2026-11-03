package com.x.bpm.audit.application;

import java.util.Map;

/** Ghi audit nghiệp vụ trong cùng transaction với thay đổi được ghi nhận. */
@FunctionalInterface
public interface AuditEventRecorder {
    void record(String action, String entityType, String entityId, Map<String, Object> diff);
}
