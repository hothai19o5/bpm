package com.x.bpm.form.domain;

import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import com.x.bpm.shared.error.ValidationIssue;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Kiểm tra cấu trúc cấu hình; validation dữ liệu hồ sơ/action thuộc P3.2. */
public final class FormSchemaContract {
    public static final String DIALECT = "bpm-form-v1";
    public static final String JSON_SCHEMA = "https://json-schema.org/draft/2020-12/schema";
    public static final String DECIMAL_PATTERN = "^-?(0|[1-9][0-9]*)(\\.[0-9]+)?$";
    public static final String MONEY_PATTERN = "^(0|[1-9][0-9]*)$";
    private static final Set<String> ROOT = Set.of("$schema", "x-bpm-dialect", "type",
            "title", "properties", "required", "additionalProperties");
    private static final Set<String> FIELD = Set.of("type", "title", "description",
            "x-field-type", "minLength", "maxLength", "minimum", "maximum", "pattern",
            "format", "enum", "items", "uniqueItems", "minItems", "maxItems");

    public void validate(Map<String, Object> schema, Map<String, Object> uiSchema) {
        require(schema != null && uiSchema != null, "schema", "Schema và UI schema là bắt buộc.");
        keys(schema, ROOT, "schema");
        require(JSON_SCHEMA.equals(schema.get("$schema")) && DIALECT.equals(schema.get("x-bpm-dialect")),
                "schema", "Dialect không được hỗ trợ.");
        require("object".equals(schema.get("type")) && Boolean.FALSE.equals(schema.get("additionalProperties")),
                "schema", "Root phải là object đóng.");
        if (schema.containsKey("title")) text(schema.get("title"), "schema.title");
        var fields = object(schema.get("properties"), "schema.properties");
        require(!fields.isEmpty() && fields.size() <= 100, "schema.properties", "Cần từ 1 đến 100 trường.");
        fields.forEach((id, value) -> {
            require(id.matches("[a-z][A-Za-z0-9_]{0,63}"), "schema.properties", "Field ID không hợp lệ.");
            validateField(object(value, "schema.properties." + id), "schema.properties." + id);
        });
        var required = list(schema.get("required"), "schema.required");
        var seen = new HashSet<Object>();
        for (var id : required) {
            require(id instanceof String && fields.containsKey(id) && seen.add(id),
                    "schema.required", "Required chứa field không tồn tại hoặc trùng.");
        }
        validateUi(uiSchema, fields.keySet());
    }

    private void validateField(Map<String, Object> field, String path) {
        keys(field, FIELD, path);
        text(field.get("title"), path + ".title");
        if (field.containsKey("description")) text(field.get("description"), path + ".description");
        FormFieldType kind;
        try {
            kind = FormFieldType.valueOf(String.valueOf(field.get("x-field-type")));
        } catch (IllegalArgumentException ex) {
            throw invalid(path, "Loại trường không được hỗ trợ.");
        }
        String type = switch (kind) {
            case INTEGER -> "integer";
            case CHECKBOX -> "boolean";
            case MULTI_SELECT, ATTACHMENT -> "array";
            default -> "string";
        };
        require(type.equals(field.get("type")), path, "Kiểu JSON không khớp loại trường.");
        var allowed = new HashSet<>(Set.of("type", "title", "description", "x-field-type"));
        switch (kind) {
            case SHORT_TEXT, LONG_TEXT -> {
                allowed.addAll(Set.of("minLength", "maxLength"));
                bounds(field, "minLength", "maxLength", path, true);
            }
            case INTEGER -> {
                allowed.addAll(Set.of("minimum", "maximum"));
                bounds(field, "minimum", "maximum", path, false);
            }
            case DECIMAL, MONEY_VND -> {
                allowed.add("pattern");
                require((kind == FormFieldType.DECIMAL ? DECIMAL_PATTERN : MONEY_PATTERN)
                        .equals(field.get("pattern")), path, "Pattern số chính xác không hợp lệ.");
            }
            case DATE -> {
                allowed.add("format");
                require("date".equals(field.get("format")), path, "Ngày phải có format date.");
            }
            case SINGLE_SELECT -> {
                allowed.add("enum");
                options(field.get("enum"), path + ".enum");
            }
            case MULTI_SELECT, ATTACHMENT -> {
                allowed.addAll(Set.of("items", "uniqueItems", "minItems", "maxItems"));
                require(Boolean.TRUE.equals(field.get("uniqueItems")), path, "Array phải uniqueItems.");
                bounds(field, "minItems", "maxItems", path, true);
                var items = object(field.get("items"), path + ".items");
                keys(items, kind == FormFieldType.ATTACHMENT ? Set.of("type", "format") : Set.of("type", "enum"), path);
                require("string".equals(items.get("type")), path, "Items phải là string.");
                if (kind == FormFieldType.ATTACHMENT) {
                    require("uuid".equals(items.get("format")), path, "Tệp chỉ tham chiếu UUID metadata.");
                    require(field.containsKey("maxItems") && number(field.get("maxItems"), path)
                            .compareTo(BigDecimal.TEN) <= 0, path, "Tối đa 10 tham chiếu tệp mỗi trường.");
                } else options(items.get("enum"), path + ".items.enum");
            }
            case CHECKBOX -> { }
        }
        keys(field, allowed, path);
    }

    private void validateUi(Map<String, Object> ui, Set<String> fields) {
        keys(ui, Set.of("version", "groups"), "uiSchema");
        require(ui.get("version") instanceof Number && number(ui.get("version"), "uiSchema.version")
                .compareTo(BigDecimal.ONE) == 0, "uiSchema.version", "UI schema version phải là 1.");
        var groups = list(ui.get("groups"), "uiSchema.groups");
        require(!groups.isEmpty() && groups.size() <= 100, "uiSchema.groups", "Cần từ 1 đến 100 nhóm.");
        var ids = new HashSet<String>();
        var assigned = new HashSet<Object>();
        for (var value : groups) {
            var group = object(value, "uiSchema.groups");
            keys(group, Set.of("id", "label", "fields"), "uiSchema.groups");
            text(group.get("id"), "uiSchema.groups.id");
            text(group.get("label"), "uiSchema.groups.label");
            require(ids.add((String) group.get("id")), "uiSchema.groups.id", "Group ID bị trùng.");
            var members = list(group.get("fields"), "uiSchema.groups.fields");
            require(!members.isEmpty(), "uiSchema.groups.fields", "Nhóm phải có trường.");
            for (var id : members) require(id instanceof String && fields.contains(id) && assigned.add(id),
                    "uiSchema.groups.fields", "Field không tồn tại hoặc xuất hiện nhiều lần.");
        }
        require(assigned.equals(fields), "uiSchema.groups", "Mỗi trường phải có đúng một vị trí trong UI.");
    }

    private void options(Object value, String path) {
        var options = list(value, path);
        require(!options.isEmpty() && options.size() <= 100, path, "Cần từ 1 đến 100 lựa chọn.");
        var seen = new HashSet<Object>();
        for (var option : options) {
            text(option, path);
            require(seen.add(option), path, "Lựa chọn bị trùng.");
        }
    }

    private void bounds(Map<String, Object> field, String min, String max, String path, boolean nonNegative) {
        for (var key : List.of(min, max)) if (field.containsKey(key)) {
            var value = number(field.get(key), path + "." + key);
            require(value.stripTrailingZeros().scale() <= 0 && (!nonNegative || value.signum() >= 0)
                    && value.abs().compareTo(new BigDecimal("9007199254740991")) <= 0,
                    path, "Giới hạn phải là số nguyên trong miền an toàn JavaScript.");
        }
        if (field.containsKey(min) && field.containsKey(max)) require(number(field.get(min), path)
                .compareTo(number(field.get(max), path)) <= 0, path, "Min không được lớn hơn max.");
    }

    private static BigDecimal number(Object value, String path) {
        require(value instanceof Number, path, "Giới hạn phải là số.");
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException ex) { throw invalid(path, "Số không hợp lệ."); }
    }

    private static void keys(Map<String, Object> value, Set<String> allowed, String path) {
        require(allowed.containsAll(value.keySet()), path, "Có thuộc tính ngoài hợp đồng được hỗ trợ.");
    }

    private static void text(Object value, String path) {
        require(value instanceof String text && !text.isBlank() && text.length() <= 2000,
                path, "Cần chuỗi không rỗng, tối đa 2000 ký tự.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String path) {
        require(value instanceof Map<?, ?> map && map.keySet().stream().allMatch(String.class::isInstance),
                path, "Cần JSON object.");
        return (Map<String, Object>) value;
    }

    private static List<?> list(Object value, String path) {
        require(value instanceof List<?>, path, "Cần JSON array.");
        return (List<?>) value;
    }

    private static void require(boolean condition, String path, String message) {
        if (!condition) throw invalid(path, message);
    }

    private static ApplicationException invalid(String path, String message) {
        return new ApplicationException(ErrorCode.VALIDATION_FAILED, "Cấu hình biểu mẫu không hợp lệ.",
                List.of(new ValidationIssue(path, message, null)));
    }
}
