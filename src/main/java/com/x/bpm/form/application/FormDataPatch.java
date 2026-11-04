package com.x.bpm.form.application;

import com.x.bpm.form.domain.JsonDocument;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.Map;
import java.util.Set;

/** Thay thế giá trị từng field; removeFields xóa tường minh, không dùng null như JSON Merge Patch. */
public record FormDataPatch(Map<String, Object> values, Set<String> removeFields) {
    public FormDataPatch {
        if (values == null || removeFields == null || values.keySet().stream().anyMatch(removeFields::contains))
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Patch biểu mẫu không hợp lệ.");
        values = JsonDocument.snapshot(values);
        removeFields = Set.copyOf(removeFields);
    }

    public FormDataPatch(Map<String, Object> values) { this(values, Set.of()); }
}
