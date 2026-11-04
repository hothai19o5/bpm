package com.x.bpm.form.application;

import com.x.bpm.form.domain.FormAction;
import com.x.bpm.form.domain.FormDataValidator;
import com.x.bpm.form.domain.FormFieldAccess;
import com.x.bpm.form.domain.FormFieldPolicy;
import com.x.bpm.form.domain.FormSchemaContract;
import com.x.bpm.form.domain.FormVersion;
import com.x.bpm.form.domain.JsonDocument;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import com.x.bpm.shared.error.ValidationIssue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cổng nội bộ dùng sau khi use case đã kiểm tra actor/resource/action/state và pin version. */
public final class FormDataService {
    private final FormSchemaContract contract = new FormSchemaContract();
    private final FormDataValidator validator = new FormDataValidator();

    public FormDataView read(FormVersion version, Map<String, Object> stored, FormFieldPolicy policy) {
        var fields = prepare(version, policy);
        if (stored == null)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Thiếu dữ liệu biểu mẫu.");
        var visible = new LinkedHashMap<String, Object>();
        var data = new LinkedHashMap<String, Object>();
        var access = new LinkedHashMap<String, FormFieldAccess>();
        fields.forEach((id, definition) -> {
            if (policy.accessTo(id) != FormFieldAccess.HIDDEN) {
                visible.put(id, definition);
                access.put(id, policy.accessTo(id));
                if (stored.containsKey(id)) data.put(id, stored.get(id));
            }
        });
        var schema = new LinkedHashMap<>(version.schema());
        schema.put("properties", visible);
        schema.put("required", list(version.schema().get("required")).stream()
                .filter(id -> policy.accessTo((String) id) == FormFieldAccess.EDITABLE).toList());
        var groups = new ArrayList<Map<String, Object>>();
        for (var item : list(version.uiSchema().get("groups"))) {
            var group = object(item);
            var members = list(group.get("fields")).stream().filter(visible::containsKey).toList();
            if (!members.isEmpty()) {
                var projected = new LinkedHashMap<>(group);
                projected.put("fields", members);
                groups.add(projected);
            }
        }
        return new FormDataView(schema, Map.of("version", 1, "groups", groups), data, access);
    }

    /** Kết quả là toàn bộ dữ liệu để lưu nội bộ; trả client phải gọi read để lọc quyền. */
    public Map<String, Object> applyPatch(FormVersion version, Map<String, Object> stored,
            FormDataPatch patch, FormFieldPolicy policy, FormAction action, String reason) {
        var fields = prepare(version, policy);
        if (action == null || stored == null || patch == null)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Thiếu ngữ cảnh hoặc dữ liệu biểu mẫu.");
        var changed = new HashSet<>(patch.values().keySet());
        changed.addAll(patch.removeFields());
        // Không trả field ID từ payload tampering, tránh tiết lộ trường HIDDEN/không tồn tại.
        if (changed.stream().anyMatch(id -> !fields.containsKey(id)
                || policy.accessTo(id) != FormFieldAccess.EDITABLE))
            throw new ApplicationException(ErrorCode.FORBIDDEN, "Patch chứa trường không được phép sửa.");
        var merged = new LinkedHashMap<>(stored);
        patch.removeFields().forEach(merged::remove);
        merged.putAll(patch.values());
        var required = requiredFields(version, policy, action);
        var issues = new ArrayList<ValidationIssue>();
        for (var issue : validator.validate(fields, merged, required)) {
            if (issue.field() == null || policy.accessTo(issue.field()) == FormFieldAccess.HIDDEN)
                issues.add(new ValidationIssue(null, "Dữ liệu biểu mẫu không hợp lệ trong ngữ cảnh hiện tại.", null));
            else issues.add(issue);
        }
        if (action.requiresReason() && (reason == null || reason.isBlank()))
            issues.add(new ValidationIssue("actionReason", "Hành động này bắt buộc có lý do.", null));
        if (reason != null && reason.length() > 4000)
            issues.add(new ValidationIssue("actionReason", "Lý do tối đa 4000 ký tự.", null));
        if (!issues.isEmpty()) throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                "Dữ liệu biểu mẫu không hợp lệ.", issues.stream().distinct().toList());
        return JsonDocument.snapshot(merged);
    }

    private Set<String> requiredFields(FormVersion version, FormFieldPolicy policy, FormAction action) {
        var required = new HashSet<String>();
        if (action.requiresCompleteFields()) {
            for (var id : list(version.schema().get("required")))
                if (policy.accessTo((String) id) == FormFieldAccess.EDITABLE) required.add((String) id);
            required.addAll(policy.requiredByAction().getOrDefault(action, Set.of()));
        }
        return required;
    }

    private Map<String, Object> prepare(FormVersion version, FormFieldPolicy policy) {
        if (version == null || policy == null) throw configurationError();
        try { contract.validate(version.schema(), version.uiSchema()); }
        catch (ApplicationException ex) { throw configurationError(); }
        var fields = object(version.schema().get("properties"));
        if (!fields.keySet().containsAll(policy.access().keySet())) throw configurationError();
        policy.requiredByAction().forEach((action, required) -> {
            if (!action.requiresCompleteFields() || required.stream().anyMatch(id -> !fields.containsKey(id)
                    || policy.accessTo(id) != FormFieldAccess.EDITABLE)) throw configurationError();
        });
        return fields;
    }

    private static ApplicationException configurationError() {
        return new ApplicationException(ErrorCode.INTERNAL_ERROR, "Cấu hình biểu mẫu hoặc quyền trường không hợp lệ.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) { return (Map<String, Object>) value; }
    private static List<?> list(Object value) { return (List<?>) value; }
}
