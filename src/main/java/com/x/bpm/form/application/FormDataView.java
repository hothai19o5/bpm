package com.x.bpm.form.application;

import com.x.bpm.form.domain.FormFieldAccess;
import com.x.bpm.form.domain.JsonDocument;
import java.util.Map;

/** View dành cho renderer, đã loại cả dữ liệu và cấu hình của trường HIDDEN. */
public record FormDataView(Map<String, Object> schema, Map<String, Object> uiSchema,
        Map<String, Object> data, Map<String, FormFieldAccess> fieldAccess) {
    public FormDataView {
        schema = JsonDocument.snapshot(schema);
        uiSchema = JsonDocument.snapshot(uiSchema);
        data = JsonDocument.snapshot(data);
        fieldAccess = Map.copyOf(fieldAccess);
    }
}
