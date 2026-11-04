package com.x.bpm.form.api;

import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.NewUserAccount;
import com.x.bpm.identity.application.PasswordHashPort;
import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.support.IsolatedPostgresTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IsolatedPostgresTest
@AutoConfigureMockMvc
class FormCatalogIntegrationTest {
    private static final String ROOT = "/api/v1/form-definitions";
    private static final String PASSWORD = "p36 test password only";
    @Autowired MockMvc mvc;
    @Autowired AuthAccountPort accounts;
    @Autowired PasswordHashPort passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void listsSearchesFiltersAndPaginatesWithStableOrderingAndLightweightResponses() throws Exception {
        var token = actor("form.design", "form.publish");
        var prefix = "catalog-" + UUID.randomUUID();
        var a = create(token, prefix + "-a", prefix + " A");
        var b = create(token, prefix + "-b", prefix + " B");
        var c = create(token, prefix + "-c", prefix + " C");
        publish(token, a);
        cloneVersion(token, a);
        jdbc.update("update form_definitions set status = 'INACTIVE' where id = ?", UUID.fromString(id(c)));
        mvc.perform(get(ROOT).header("Authorization", bearer(token)).param("q", prefix.toUpperCase()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(id(a))).andExpect(jsonPath("$.data.items[0].schema").doesNotExist());
        mvc.perform(get(ROOT).header("Authorization", bearer(token)).param("q", prefix).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(id(b)));
        mvc.perform(get(ROOT).header("Authorization", bearer(token)).param("q", prefix).param("status", "ACTIVE").param("versionStatus", "PUBLISHED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.items[0].id").value(id(a)));
        mvc.perform(get(ROOT).header("Authorization", bearer(token)).param("q", prefix).param("status", "INACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(id(c)));
        for (var term : new String[]{"%", "' or 1=1 --", "no-such-" + UUID.randomUUID()})
            mvc.perform(get(ROOT).header("Authorization", bearer(token)).param("q", term))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get(ROOT).header("Authorization", bearer(token)).param("q", prefix).param("page", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void detailAndHistoryExposeAllVersionsWithoutSchemaAndVerifyDefinitionOwnershipOfVersion() throws Exception {
        var token = actor("form.design", "form.publish");
        var source = create(token, "history-" + UUID.randomUUID(), "History");
        publish(token, source);
        var draft = cloneVersion(token, source);
        mvc.perform(get(ROOT + "/" + id(source)).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.key").value(source.get("definition").get("key").asString()));
        mvc.perform(get(ROOT + "/" + id(source) + "/versions").header("Authorization", bearer(token)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(vid(draft))).andExpect(jsonPath("$.data.items[0].schema").doesNotExist());
        mvc.perform(get(ROOT + "/" + id(source) + "/versions").header("Authorization", bearer(token)).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.items[0].publishedAt").isNotEmpty());
        mvc.perform(get(ROOT + "/" + UUID.randomUUID() + "/versions").header("Authorization", bearer(token))).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/" + UUID.randomUUID() + "/versions/" + vid(source)).header("Authorization", bearer(token))).andExpect(status().isNotFound());
    }

    @Test
    void bindingReturnsOnlyActivePublishedVersionsAndRechecksDirectSelection() throws Exception {
        var admin = actor("form.design", "form.publish");
        var designer = actor("process.design");
        var prefix = "binding-" + UUID.randomUUID();
        var source = create(admin, prefix + "-a", prefix);
        var hidden = create(admin, prefix + "-b", prefix);
        publish(admin, source);
        var draft = cloneVersion(admin, source);
        publish(admin, hidden);
        jdbc.update("update form_definitions set status = 'INACTIVE' where id = ?", UUID.fromString(id(hidden)));
        mvc.perform(get(ROOT + "/binding-options").header("Authorization", bearer(designer)).param("q", prefix).param("size", "1")
                        .param("status", "INACTIVE").param("versionStatus", "DRAFT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].version.id").value(vid(source)))
                .andExpect(jsonPath("$.data.items[0].version.schema").doesNotExist());
        mvc.perform(get(ROOT + "/binding-options/" + vid(source)).header("Authorization", bearer(designer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version.schema.properties.amount").exists());
        for (var excluded : new String[]{vid(draft), vid(hidden), UUID.randomUUID().toString()})
            mvc.perform(get(ROOT + "/binding-options/" + excluded).header("Authorization", bearer(designer))).andExpect(status().isNotFound());
        jdbc.update("update form_versions set status = 'RETIRED' where id = ?", UUID.fromString(vid(source)));
        mvc.perform(get(ROOT + "/binding-options/" + vid(source)).header("Authorization", bearer(designer))).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/binding-options").header("Authorization", bearer(designer)).param("q", prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void permissionsSeparateManagementBindingAndEmployeesIncludingRevocation() throws Exception {
        var admin = actor("form.design", "form.publish");
        var source = create(admin, "scope-" + UUID.randomUUID(), "Scope");
        publish(admin, source);
        var publisher = actor("form.publish");
        var processDesigner = actor("process.design");
        var employee = actor("record.create");
        var urls = new String[]{ROOT, ROOT + "/" + id(source), ROOT + "/" + id(source) + "/versions", previewUrl(source) + "?context=REQUESTER"};
        for (var url : urls) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).header("Authorization", bearer(employee))).andExpect(status().isForbidden());
            mvc.perform(get(url).header("Authorization", bearer(processDesigner))).andExpect(status().isForbidden());
            mvc.perform(get(url).header("Authorization", bearer(publisher))).andExpect(status().isOk());
        }
        for (var url : new String[]{ROOT + "/binding-options", ROOT + "/binding-options/" + vid(source)}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            for (var token : new String[]{employee, publisher, admin})
                mvc.perform(get(url).header("Authorization", bearer(token))).andExpect(status().isForbidden());
        }
        var userId = json.readTree(mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(processDesigner)))
                .andReturn().getResponse().getContentAsString()).get("data").get("id").asString();
        jdbc.update("delete from user_roles where user_id = ?", UUID.fromString(userId));
        mvc.perform(get(ROOT + "/binding-options").header("Authorization", bearer(processDesigner))).andExpect(status().isForbidden());
        mvc.perform(post(previewUrl(source)).header("Authorization", bearer(employee)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidQueriesAndSortWithoutExposingInternalErrors() throws Exception {
        var token = actor("form.design");
        var source = create(token, "query-" + UUID.randomUUID(), "Query");
        for (var pair : new String[][]{{"page", "-1"}, {"size", "101"}, {"size", "0"}, {"sort", "ownerUserId,asc"},
                {"sort", "name;delete,asc"}, {"status", "OTHER"}, {"versionStatus", "OTHER"}, {"page", "2147483647"}, {"q", "x".repeat(201)}})
            mvc.perform(get(ROOT).header("Authorization", bearer(token)).param(pair[0], pair[1])).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/" + id(source) + "/versions").header("Authorization", bearer(token)).param("sort", "name,asc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(previewUrl(source)).header("Authorization", bearer(token)).param("context", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewUsesServerPoliciesValidatesActionsAndNeverPersistsDataOrAudit() throws Exception {
        var token = actor("form.design");
        var source = create(token, "preview-" + UUID.randomUUID(), "Preview");
        var before = jdbc.queryForObject("select count(*) from audit_events where entity_id = ?", Long.class, id(source));
        mvc.perform(get(previewUrl(source)).header("Authorization", bearer(token)).param("context", "REQUESTER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.view.fieldAccess.amount").value("EDITABLE"))
                .andExpect(jsonPath("$.data.view.fieldAccess.budgetCode").value("READ_ONLY"));
        mvc.perform(get(previewUrl(source)).header("Authorization", bearer(token)).param("context", "ACCOUNTING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.view.fieldAccess.amount").value("READ_ONLY"))
                .andExpect(jsonPath("$.data.view.fieldAccess.budgetCode").value("EDITABLE"))
                .andExpect(jsonPath("$.data.view.schema.required.length()").value(2));
        check(token, source, "REQUESTER", "SAVE_DRAFT", Map.of(), 200, null);
        mvc.perform(post(previewUrl(source)).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("context", "REQUESTER", "patch", Map.of("values", Map.of(), "removeFields", java.util.List.of())))))
                .andExpect(status().isBadRequest());
        check(token, source, "REQUESTER", "SUBMIT", Map.of(), 422, null);
        check(token, source, "REQUESTER", "SUBMIT", Map.of("itemName", "Laptop", "quantity", 2, "amount", "50000000", "reason", "Work"), 200, null);
        check(token, source, "ACCOUNTING", "APPROVE", Map.of("budgetCode", "IT-26", "budgetConfirmed", true), 200, null);
        check(token, source, "ACCOUNTING", "REQUEST_CHANGES", Map.of(), 422, null);
        check(token, source, "ACCOUNTING", "REQUEST_CHANGES", Map.of(), 200, "Bổ sung chứng từ");
        check(token, source, "ACCOUNTING", "SUBMIT", Map.of(), 403, null);
        check(token, source, "REQUESTER", "SAVE_DRAFT", Map.of("budgetCode", "tampered"), 403, null);
        check(token, source, "ACCOUNTING", "APPROVE", Map.of("amount", "1"), 403, null);
        check(token, source, "REQUESTER", "SAVE_DRAFT", Map.of("notInSchema", "secret"), 403, null);
        mvc.perform(post(previewUrl(source)).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("context", "REQUESTER", "action", "SAVE_DRAFT",
                        "patch", Map.of("values", Map.of(), "removeFields", java.util.List.of("budgetCode"))))))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select revision from form_versions where id = ?", Long.class, UUID.fromString(vid(source)))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_events where entity_id = ?", Long.class, id(source))).isEqualTo(before);
        mvc.perform(get(previewUrl(source)).header("Authorization", bearer(token)).param("context", "REQUESTER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.view.data").isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewOmitsUnknownFieldsAndStoredVersionContentIsNotChanged() throws Exception {
        var token = actor("form.design");
        var source = create(token, "hidden-" + UUID.randomUUID(), "Hidden");
        var schema = json.convertValue(source.get("version").get("schema"), Map.class);
        ((Map<String, Object>) schema.get("properties")).put("internalMemo", Map.of("type", "string", "title", "SECRET LABEL", "x-field-type", "SHORT_TEXT"));
        var ui = json.convertValue(source.get("version").get("uiSchema"), Map.class);
        ((java.util.List<String>) ((Map<?, ?>) ((java.util.List<?>) ui.get("groups")).get(0)).get("fields")).add("internalMemo");
        mvc.perform(put(ROOT + "/" + id(source) + "/draft").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("versionId", vid(source), "expectedRevision", 0, "schema", schema, "uiSchema", ui))))
                .andExpect(status().isOk());
        var response = mvc.perform(get(previewUrl(source)).header("Authorization", bearer(token)).param("context", "REQUESTER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("internalMemo", "SECRET LABEL");
        check(token, source, "REQUESTER", "SAVE_DRAFT", Map.of("internalMemo", "tampered"), 403, null);
    }

    private void check(String token, JsonNode form, String context, String action, Map<String, Object> values, int code, String reason) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("context", context); body.put("action", action); body.put("reason", reason);
        body.put("patch", Map.of("values", values, "removeFields", java.util.List.of()));
        mvc.perform(post(previewUrl(form)).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().is(code));
    }

    @SuppressWarnings("unchecked")
    private JsonNode create(String token, String key, String name) throws Exception {
        Map<String, Object> body;
        try (var stream = getClass().getResourceAsStream("/forms/purchase-request.v1.json")) { body = json.readValue(stream, Map.class); }
        body.put("key", key); body.put("name", name);
        return json.readTree(mvc.perform(post(ROOT).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }

    private void publish(String token, JsonNode form) throws Exception { operation(token, form, "publish"); }
    private JsonNode cloneVersion(String token, JsonNode form) throws Exception {
        // Publish increments revision; reload the source before invoking clone.
        var current = json.readTree(mvc.perform(get(ROOT + "/" + id(form) + "/versions/" + vid(form)).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).get("data");
        return operation(token, current, "clone");
    }
    private JsonNode operation(String token, JsonNode form, String op) throws Exception {
        return json.readTree(mvc.perform(post(ROOT + "/" + id(form) + "/" + op).header("Authorization", bearer(token))
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("versionId", vid(form), "expectedRevision", form.get("version").get("revision").asLong()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
    private String id(JsonNode form) { return form.get("definition").get("id").asString(); }
    private String vid(JsonNode form) { return form.get("version").get("id").asString(); }
    private String previewUrl(JsonNode form) { return ROOT + "/" + id(form) + "/versions/" + vid(form) + "/preview"; }
    private String bearer(String token) { return "Bearer " + token; }

    private String actor(String... permissions) throws Exception {
        var user = accounts.create(new NewUserAccount("p36-" + UUID.randomUUID(), "P3.6 Tester", passwords.hash(PASSWORD), AccountStatus.ACTIVE, false));
        var role = UUID.randomUUID();
        jdbc.update("insert into roles (id, created_at, updated_at, revision, code, name, status) values (?, current_timestamp, current_timestamp, 0, ?, 'P3.6', 'ACTIVE')", role, "p36-" + role);
        jdbc.update("insert into user_roles (id, created_at, updated_at, revision, user_id, role_id) values (?, current_timestamp, current_timestamp, 0, ?, ?)", UUID.randomUUID(), user.id(), role);
        for (var permission : permissions) jdbc.update("insert into role_permissions (id, created_at, updated_at, revision, role_id, permission_id) values (?, current_timestamp, current_timestamp, 0, ?, ?)",
                UUID.randomUUID(), role, jdbc.queryForObject("select id from permissions where code = ?", UUID.class, permission));
        return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("username", user.username(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data").get("accessToken").asString();
    }
}
