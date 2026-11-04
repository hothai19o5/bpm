package com.x.bpm.form.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.x.bpm.shared.error.ApplicationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FormSchemaContractTest {
    private final FormSchemaContract contract = new FormSchemaContract();

    @Test
    void acceptsPurchaseFixtureAndMakesDeepImmutableSnapshot() throws Exception {
        var payload = fixture();
        contract.validate(map(payload.get("schema")), map(payload.get("uiSchema")));
        var snapshot = JsonDocument.snapshot(payload);
        map(payload.get("schema")).put("title", "Changed");
        assertThat(map(snapshot.get("schema")).get("title")).isEqualTo("Đề nghị mua sắm");
        assertThatThrownBy(() -> map(snapshot.get("schema")).put("title", "Mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void supportsAllTenTypesAndRejectsNumericMoneyAndRemoteReferences() throws Exception {
        var payload = fixture();
        var schema = map(payload.get("schema"));
        var properties = map(schema.get("properties"));
        var ui = map(payload.get("uiSchema"));
        properties.put("decimal", Map.of("type", "string", "title", "Decimal", "x-field-type", "DECIMAL",
                "pattern", FormSchemaContract.DECIMAL_PATTERN));
        properties.put("single", Map.of("type", "string", "title", "Single", "x-field-type", "SINGLE_SELECT",
                "enum", List.of("A", "B")));
        properties.put("multi", Map.of("type", "array", "title", "Multi", "x-field-type", "MULTI_SELECT",
                "items", Map.of("type", "string", "enum", List.of("A", "B")), "uniqueItems", true));
        ui.put("groups", List.of(Map.of("id", "all", "label", "All", "fields", List.copyOf(properties.keySet()))));
        contract.validate(schema, ui);
        map(properties.get("amount")).put("type", "number");
        rejected(payload);
        map(properties.get("amount")).put("type", "string");
        schema.put("$ref", "https://example.invalid/schema");
        rejected(payload);
    }

    @Test
    void rejectsPermissionsUnknownRequiredAndBrokenUiReferences() throws Exception {
        for (String badKey : List.of("permissions", "readOnly", "hidden", "default")) {
            var payload = fixture();
            map(map(map(payload.get("schema")).get("properties")).get("amount")).put(badKey, true);
            rejected(payload);
        }
        var payload = fixture();
        map(payload.get("schema")).put("required", List.of("deletedField"));
        rejected(payload);
        payload = fixture();
        map(payload.get("uiSchema")).put("groups", List.of(Map.of("id", "test", "label", "Test",
                "fields", List.of("amount", "amount"))));
        rejected(payload);
    }

    @Test
    void rejectsInvalidLimitsAndAttachmentItems() throws Exception {
        var payload = fixture();
        var fields = map(map(payload.get("schema")).get("properties"));
        map(fields.get("quantity")).put("minimum", 1.5);
        rejected(payload);
        map(fields.get("quantity")).put("minimum", 1);
        map(fields.get("proposalAttachments")).put("maxItems", 11);
        rejected(payload);
        map(fields.get("proposalAttachments")).put("maxItems", 10);
        map(fields.get("proposalAttachments")).put("items", Map.of("type", "string", "format", "uri"));
        rejected(payload);
    }

    private void rejected(Map<String, Object> payload) {
        assertThatThrownBy(() -> contract.validate(map(payload.get("schema")), map(payload.get("uiSchema"))))
                .isInstanceOf(ApplicationException.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> fixture() throws Exception {
        try (var source = FormSchemaContractTest.class.getResourceAsStream("/forms/purchase-request.v1.json")) {
            return new ObjectMapper().readValue(source, LinkedHashMap.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
}
