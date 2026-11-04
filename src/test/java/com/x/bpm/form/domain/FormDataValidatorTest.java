package com.x.bpm.form.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FormDataValidatorTest {
    private final FormDataValidator validator = new FormDataValidator();

    @ParameterizedTest(name = "{0}: {1} -> valid={2}")
    @MethodSource("cases")
    void validatesTypesAndBounds(FormFieldType type, Object value, boolean valid) {
        var data = new HashMap<String, Object>();
        data.put("field", value);
        var result = validator.validate(Map.of("field", field(type)), data, Set.of());
        if (valid) assertThat(result).isEmpty();
        else assertThat(result).isNotEmpty().allSatisfy(issue -> assertThat(issue.field()).isEqualTo("field"));
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(FormFieldType.SHORT_TEXT, "😀a", true),
                Arguments.of(FormFieldType.SHORT_TEXT, "abc", false),
                Arguments.of(FormFieldType.SHORT_TEXT, "", false),
                Arguments.of(FormFieldType.LONG_TEXT, "ab", true),
                Arguments.of(FormFieldType.LONG_TEXT, 2, false),
                Arguments.of(FormFieldType.INTEGER, 1, true),
                Arguments.of(FormFieldType.INTEGER, new BigDecimal("2.0"), true),
                Arguments.of(FormFieldType.INTEGER, 0, false),
                Arguments.of(FormFieldType.INTEGER, 4, false),
                Arguments.of(FormFieldType.INTEGER, 1.5, false),
                Arguments.of(FormFieldType.INTEGER, "2", false),
                Arguments.of(FormFieldType.INTEGER, new BigInteger("9007199254740992"), false),
                Arguments.of(FormFieldType.INTEGER, Double.NaN, false),
                Arguments.of(FormFieldType.DECIMAL, "-12345678901234567890.123456789", true),
                Arguments.of(FormFieldType.DECIMAL, "0", true),
                Arguments.of(FormFieldType.DECIMAL, "1e2", false),
                Arguments.of(FormFieldType.DECIMAL, "01.2", false),
                Arguments.of(FormFieldType.DECIMAL, 1.5, false),
                Arguments.of(FormFieldType.MONEY_VND, "90071992547409931234567890", true),
                Arguments.of(FormFieldType.MONEY_VND, "0", true),
                Arguments.of(FormFieldType.MONEY_VND, "1.5", false),
                Arguments.of(FormFieldType.MONEY_VND, "-1", false),
                Arguments.of(FormFieldType.MONEY_VND, "1,000", false),
                Arguments.of(FormFieldType.MONEY_VND, "+10", false),
                Arguments.of(FormFieldType.MONEY_VND, 50000000, false),
                Arguments.of(FormFieldType.DATE, "2024-02-29", true),
                Arguments.of(FormFieldType.DATE, "2026-02-29", false),
                Arguments.of(FormFieldType.DATE, "2026-11-04T00:00:00Z", false),
                Arguments.of(FormFieldType.DATE, "0000-01-01", false),
                Arguments.of(FormFieldType.SINGLE_SELECT, "A", true),
                Arguments.of(FormFieldType.SINGLE_SELECT, "C", false),
                Arguments.of(FormFieldType.MULTI_SELECT, List.of("A", "B"), true),
                Arguments.of(FormFieldType.MULTI_SELECT, List.of("A", "A"), false),
                Arguments.of(FormFieldType.MULTI_SELECT, List.of("C"), false),
                Arguments.of(FormFieldType.MULTI_SELECT, Arrays.asList("A", null), false),
                Arguments.of(FormFieldType.MULTI_SELECT, List.of(), false),
                Arguments.of(FormFieldType.CHECKBOX, false, true),
                Arguments.of(FormFieldType.CHECKBOX, "true", false),
                Arguments.of(FormFieldType.ATTACHMENT, List.of("20000000-0000-0000-0000-000000000001"), true),
                Arguments.of(FormFieldType.ATTACHMENT, List.of("1-1-1-1-1"), false),
                Arguments.of(FormFieldType.ATTACHMENT, List.of("https://example.invalid/file"), false),
                Arguments.of(FormFieldType.ATTACHMENT, List.of(Map.of("id", "fake")), false),
                Arguments.of(FormFieldType.ATTACHMENT, null, false));
    }

    @Test
    void requiredAcceptsFalseAndZeroButRejectsBlankAndEmpty() {
        var fields = Map.<String, Object>of("flag", field(FormFieldType.CHECKBOX),
                "money", field(FormFieldType.MONEY_VND), "text", field(FormFieldType.LONG_TEXT),
                "options", field(FormFieldType.MULTI_SELECT));
        var issues = validator.validate(fields, Map.of("flag", false, "money", "0", "text", " ",
                "options", List.of()), fields.keySet());
        assertThat(issues).noneMatch(issue -> "flag".equals(issue.field()) || "money".equals(issue.field()));
        assertThat(issues).anyMatch(issue -> "text".equals(issue.field())).anyMatch(issue -> "options".equals(issue.field()));
    }

    @Test
    void enforcesGlobalAttachmentLimitAcrossFields() {
        var ids = Stream.iterate(1, i -> i + 1).limit(6)
                .map(i -> "20000000-0000-0000-0000-%012d".formatted(i)).toList();
        var issues = validator.validate(Map.of("first", field(FormFieldType.ATTACHMENT),
                "second", field(FormFieldType.ATTACHMENT)), Map.of("first", ids, "second", ids), Set.of());
        assertThat(issues).anyMatch(issue -> issue.field() == null);
    }

    @Test
    void rejectsUnknownAndNullFieldsWithoutEchoingValues() {
        var data = new HashMap<String, Object>();
        data.put("unknown", "secret-payload");
        data.put("field", null);
        var issues = validator.validate(Map.of("field", field(FormFieldType.SHORT_TEXT)), data, Set.of());
        assertThat(issues).hasSize(2);
        assertThat(issues.toString()).doesNotContain("secret-payload");
    }

    private static Map<String, Object> field(FormFieldType kind) {
        return switch (kind) {
            case SHORT_TEXT, LONG_TEXT -> Map.of("x-field-type", kind.name(), "minLength", 1, "maxLength", 2);
            case INTEGER -> Map.of("x-field-type", kind.name(), "minimum", 1, "maximum", 3);
            case SINGLE_SELECT -> Map.of("x-field-type", kind.name(), "enum", List.of("A", "B"));
            case MULTI_SELECT -> Map.of("x-field-type", kind.name(), "items", Map.of("enum", List.of("A", "B")),
                    "minItems", 1, "maxItems", 2);
            case ATTACHMENT -> Map.of("x-field-type", kind.name(), "maxItems", 10);
            default -> Map.of("x-field-type", kind.name());
        };
    }
}
