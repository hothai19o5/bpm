package com.x.bpm.form.domain;

import com.x.bpm.shared.error.ValidationIssue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validate dữ liệu theo schema bpm-form-v1 đã được kiểm tra cấu trúc. Không ép kiểu. */
public final class FormDataValidator {
    private static final BigDecimal SAFE_INTEGER = new BigDecimal("9007199254740991");
    private static final Pattern DECIMAL = Pattern.compile(FormSchemaContract.DECIMAL_PATTERN);
    private static final Pattern MONEY = Pattern.compile(FormSchemaContract.MONEY_PATTERN);
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    public List<ValidationIssue> validate(Map<String, Object> properties, Map<String, Object> data,
            Set<String> required) {
        var issues = new ArrayList<ValidationIssue>();
        long attachments = 0;
        for (var entry : data.entrySet()) {
            var id = entry.getKey();
            if (!properties.containsKey(id)) {
                issues.add(issue(id, "Trường không thuộc biểu mẫu."));
                continue;
            }
            var field = object(properties.get(id));
            var kind = FormFieldType.valueOf((String) field.get("x-field-type"));
            var value = entry.getValue();
            var error = validateValue(kind, field, value);
            if (error != null) issues.add(issue(id, error));
            if (kind == FormFieldType.ATTACHMENT && value instanceof List<?> list) attachments += list.size();
        }
        for (var id : required) {
            var value = data.get(id);
            if (!data.containsKey(id) || value == null || value instanceof String text && text.isBlank()
                    || value instanceof List<?> list && list.isEmpty()) {
                issues.add(issue(id, "Trường bắt buộc chưa được điền."));
            }
        }
        if (attachments > 10) issues.add(issue(null, "Biểu mẫu chỉ được có tối đa 10 tham chiếu tệp."));
        return List.copyOf(issues);
    }

    private String validateValue(FormFieldType kind, Map<String, Object> field, Object value) {
        if (value == null) return "Không chấp nhận null; dùng thao tác xóa trường nếu cần bỏ giá trị.";
        return switch (kind) {
            case SHORT_TEXT, LONG_TEXT -> text(field, value);
            case INTEGER -> integer(field, value);
            case DECIMAL -> exactNumber(value, false);
            case MONEY_VND -> exactNumber(value, true);
            case DATE -> date(value);
            case SINGLE_SELECT -> value instanceof String && ((List<?>) field.get("enum")).contains(value)
                    ? null : "Giá trị không thuộc danh sách lựa chọn.";
            case MULTI_SELECT, ATTACHMENT -> array(kind, field, value);
            case CHECKBOX -> value instanceof Boolean ? null : "Cần giá trị boolean.";
        };
    }

    private String text(Map<String, Object> field, Object value) {
        if (!(value instanceof String text)) return "Cần giá trị chuỗi.";
        int length = text.codePointCount(0, text.length());
        return within(field, "minLength", "maxLength", BigDecimal.valueOf(length)) ? null
                : "Độ dài chuỗi ngoài giới hạn.";
    }

    private String integer(Map<String, Object> field, Object value) {
        if (!(value instanceof Number number)) return "Cần số nguyên JSON.";
        try {
            var integer = new BigDecimal(number.toString());
            if (integer.stripTrailingZeros().scale() > 0 || integer.abs().compareTo(SAFE_INTEGER) > 0)
                return "Cần số nguyên trong miền an toàn JavaScript.";
            return within(field, "minimum", "maximum", integer) ? null : "Số nguyên ngoài giới hạn.";
        } catch (NumberFormatException ex) {
            return "Số nguyên không hợp lệ.";
        }
    }

    private String exactNumber(Object value, boolean money) {
        if (!(value instanceof String text) || !(money ? MONEY : DECIMAL).matcher(text).matches())
            return money ? "Cần chuỗi tiền VND không âm, nguyên đồng." : "Cần chuỗi số thập phân chính xác.";
        // Chặn đầu vào vượt miền biểu diễn của Java, không chuyển qua double/float.
        try {
            if (money) new BigInteger(text);
            else new BigDecimal(text);
            return null;
        } catch (NumberFormatException ex) {
            return "Số chính xác không hợp lệ.";
        }
    }

    private String date(Object value) {
        if (!(value instanceof String text) || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            return "Cần ngày định dạng YYYY-MM-DD.";
        try {
            return LocalDate.parse(text).getYear() >= 1 ? null : "Năm phải từ 0001 đến 9999.";
        } catch (DateTimeParseException ex) {
            return "Ngày lịch không hợp lệ.";
        }
    }

    private String array(FormFieldType kind, Map<String, Object> field, Object value) {
        if (!(value instanceof List<?> list)) return "Cần mảng giá trị.";
        if (!within(field, "minItems", "maxItems", BigDecimal.valueOf(list.size())))
            return "Số phần tử ngoài giới hạn.";
        var seen = new HashSet<Object>();
        for (var item : list) {
            if (!(item instanceof String text)) return "Phần tử phải là chuỗi.";
            if (!seen.add(item)) return "Mảng không được chứa phần tử trùng.";
            if (kind == FormFieldType.ATTACHMENT) {
                if (!UUID.matcher(text).matches()) return "Tệp phải tham chiếu UUID chuẩn viết thường.";
            } else if (!((List<?>) object(field.get("items")).get("enum")).contains(item))
                return "Giá trị không thuộc danh sách lựa chọn.";
        }
        return null;
    }

    private boolean within(Map<String, Object> field, String min, String max, BigDecimal value) {
        return (!field.containsKey(min) || value.compareTo(new BigDecimal(field.get(min).toString())) >= 0)
                && (!field.containsKey(max) || value.compareTo(new BigDecimal(field.get(max).toString())) <= 0);
    }

    private static ValidationIssue issue(String id, String message) {
        return new ValidationIssue(id, message, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) { return (Map<String, Object>) value; }
}
