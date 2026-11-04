package com.x.bpm.form.api;

import com.x.bpm.identity.api.AuthController;
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
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@IsolatedPostgresTest
@AutoConfigureMockMvc
class FormDefinitionIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private AuthAccountPort accounts;
    @Autowired private PasswordHashPort passwordHashes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @SuppressWarnings("unchecked")
    void createsAndReadsInitialDraftWithAuditAndValidatesConfiguration() throws Exception {
        var password = "correct horse battery staple";
        var admin = accounts.create(new NewUserAccount("p31-admin-" + UUID.randomUUID(),
                "Form Admin", passwordHashes.hash(password), AccountStatus.ACTIVE, false));
        grantPermissions(admin.id(), "form.design");
        var token = login(admin.username(), password);
        Map<String, Object> payload;
        try (var stream = getClass().getResourceAsStream("/forms/purchase-request.v1.json")) {
            payload = objectMapper.readValue(stream, Map.class);
        }
        var key = "purchase-" + UUID.randomUUID();
        payload.put("key", key);
        var created = mvc.perform(post("/api/v1/form-definitions")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.definition.ownerUserId").value(admin.id().toString()))
                .andExpect(jsonPath("$.data.version.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version.versionNumber").value(1)).andReturn();
        var data = objectMapper.readTree(created.getResponse().getContentAsString()).get("data");
        var id = data.get("definition").get("id").asString();
        var versionId = data.get("version").get("id").asString();
        mvc.perform(get("/api/v1/form-definitions/" + id + "/versions/" + versionId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version.schema.properties.amount.type").value("string"));
        mvc.perform(get("/api/v1/form-definitions/" + UUID.randomUUID() + "/versions/" + versionId)
                .header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/form-definitions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from form_definitions where key = ?", Long.class, key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where entity_id = ? and action = 'FORM_CREATED'",
                Long.class, id)).isEqualTo(1);
        payload.put("key", "invalid-" + UUID.randomUUID());
        ((Map<String, Object>) payload.get("schema")).put("permissions", Map.of());
        mvc.perform(post("/api/v1/form-definitions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.details[0].field").value("schema"));
        assertThat(jdbc.queryForObject("select count(*) from form_definitions where key = ?", Long.class, payload.get("key"))).isZero();
    }

    @Test
    void deniesAnonymousAndEmployeeWithoutFormDesign() throws Exception {
        var url = "/api/v1/form-definitions/" + UUID.randomUUID() + "/versions/" + UUID.randomUUID();
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        var password = "correct horse battery staple";
        var employee = accounts.create(new NewUserAccount("p31-user-" + UUID.randomUUID(),
                "Employee", passwordHashes.hash(password), AccountStatus.ACTIVE, false));
        var token = login(employee.username(), password);
        mvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/form-definitions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/form-definitions/" + UUID.randomUUID() + "/draft")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/form-definitions/" + UUID.randomUUID() + "/draft")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesAndReopensDraftWithStableFieldIdsAndRejectsStaleRevisionOrPublishedVersion() throws Exception {
        var password = "correct horse battery staple";
        var admin = accounts.create(new NewUserAccount("p34-admin-" + UUID.randomUUID(),
                "Form Builder Admin", passwordHashes.hash(password), AccountStatus.ACTIVE, false));
        grantPermissions(admin.id(), "form.design");
        var token = login(admin.username(), password);
        Map<String, Object> payload;
        try (var stream = getClass().getResourceAsStream("/forms/purchase-request.v1.json")) {
            payload = objectMapper.readValue(stream, Map.class);
        }
        payload.put("key", "builder-" + UUID.randomUUID());
        var created = mvc.perform(post("/api/v1/form-definitions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk()).andReturn();
        var data = objectMapper.readTree(created.getResponse().getContentAsString()).get("data");
        var id = data.get("definition").get("id").asString();
        var versionId = data.get("version").get("id").asString();
        var schema = (Map<String, Object>) payload.get("schema");
        var properties = (Map<String, Object>) schema.get("properties");
        ((Map<String, Object>) properties.get("itemName")).put("title", "Nhãn mới giữ ID");
        var update = new java.util.LinkedHashMap<String, Object>();
        update.put("versionId", versionId);
        update.put("expectedRevision", 0);
        update.put("schema", schema);
        update.put("uiSchema", payload.get("uiSchema"));
        var url = "/api/v1/form-definitions/" + id + "/draft";
        mvc.perform(put(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version.revision").value(1));
        mvc.perform(get("/api/v1/form-definitions/" + id + "/versions/" + versionId)
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version.schema.properties.itemName.title").value("Nhãn mới giữ ID"))
                .andExpect(jsonPath("$.data.version.uiSchema.groups[0].fields[0]").value("itemName"));
        mvc.perform(put(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(update))).andExpect(status().isConflict());
        update.put("expectedRevision", 1);
        mvc.perform(put("/api/v1/form-definitions/" + UUID.randomUUID() + "/draft")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(update))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from audit_events where entity_id = ? and action = 'FORM_DRAFT_SAVED'",
                Long.class, id)).isEqualTo(1);
        update.remove("expectedRevision");
        mvc.perform(put(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(update))).andExpect(status().isBadRequest());
        update.put("expectedRevision", 1);
        ((Map<String, Object>) properties.get("itemName")).put("readOnly", true);
        mvc.perform(put(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(update))).andExpect(status().isUnprocessableContent());
        ((Map<String, Object>) properties.get("itemName")).remove("readOnly");
        jdbc.update("update form_versions set status = 'PUBLISHED', published_at = current_timestamp, published_by = ? where id = ?",
                admin.id(), UUID.fromString(versionId));
        mvc.perform(put(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(update))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select revision from form_versions where id = ?", Long.class, UUID.fromString(versionId))).isEqualTo(1);
    }

    private String login(String username, String password) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asString();
    }

    private void grantPermissions(UUID userId, String... permissionCodes) {
        var roleId = UUID.randomUUID();
        jdbc.update("""
                insert into roles (id, created_at, updated_at, revision, code, name, status)
                values (?, current_timestamp, current_timestamp, 0, ?, ?, 'ACTIVE')
                """, roleId, "p31-admin-" + roleId, "P3.1 Admin");
        jdbc.update("""
                insert into user_roles (id, created_at, updated_at, revision, user_id, role_id)
                values (?, current_timestamp, current_timestamp, 0, ?, ?)
                """, UUID.randomUUID(), userId, roleId);
        for (var code : permissionCodes) {
            var permissionId = jdbc.queryForObject(
                    "select id from permissions where code = ?", UUID.class, code);
            jdbc.update("""
                    insert into role_permissions (id, created_at, updated_at, revision, role_id, permission_id)
                    values (?, current_timestamp, current_timestamp, 0, ?, ?)
                    """, UUID.randomUUID(), roleId, permissionId);
        }
    }
}
