package com.x.bpm.process.api;

import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.NewUserAccount;
import com.x.bpm.identity.application.PasswordHashPort;
import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.process.application.*;
import com.x.bpm.support.IsolatedPostgresTest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IsolatedPostgresTest
@AutoConfigureMockMvc
class ProcessCatalogIntegrationTest {
    private static final String ROOT = "/api/v1/process-definitions";
    private static final String PASSWORD = "p41 test password only";
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><process id=\"purchase\" isExecutable=\"true\"><userTask id=\"review\" name=\"Duyệt\"/></process></definitions>";
    @Autowired MockMvc mvc;
    @Autowired AuthAccountPort accounts;
    @Autowired PasswordHashPort passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ProcessStore store;
    @Autowired ProcessTransactionRunner transactions;

    @Test
    void createsAtomicDefinitionAndEmptyDraftWithoutDeployingAndRecordsAudit() throws Exception {
        var designer = actor("process.design");
        var deployments = jdbc.queryForObject("select count(*) from flowable.act_re_deployment", Long.class);
        var created = create(designer, key(), "  Mua sắm  ");
        assertThat(created.get("definition").get("name").asString()).isEqualTo("Mua sắm");
        assertThat(created.get("definition").get("ownerUserId").asString()).isEqualTo(designer.id().toString());
        assertThat(created.get("definition").get("activeProcessVersionId").isNull()).isTrue();
        assertThat(created.get("version").get("metadata").get("versionNumber").asInt()).isEqualTo(1);
        assertThat(created.get("version").get("metadata").get("status").asString()).isEqualTo("DRAFT");
        assertThat(created.get("version").get("metadata").get("revision").asLong()).isZero();
        assertThat(created.get("version").get("engineVersion").isNull()).isTrue();
        assertThat(created.get("version").get("bpmnXml").isNull()).isTrue();
        assertThat(created.get("version").get("stepConfig").isEmpty()).isTrue();
        assertThat(created.get("version").get("formBindings").isEmpty()).isTrue();
        mvc.perform(get(ROOT + "/" + id(created)).header("Authorization", designer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id(created)));
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'PROCESS_CREATED' and entity_id = ?", Long.class, id(created))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from flowable.act_re_deployment", Long.class)).isEqualTo(deployments);
    }

    @Test
    void supportsExplicitActiveOwnerWithoutGivingOwnerManagementRights() throws Exception {
        var designer = actor("process.design");
        var owner = actor("process.monitor.owned");
        var created = postCreate(designer, Map.of("key", key(), "name", "Owner", "ownerUserId", owner.id()), 200);
        assertThat(created.get("definition").get("ownerUserId").asString()).isEqualTo(owner.id().toString());
        mvc.perform(get(versionUrl(created)).header("Authorization", owner.bearer())).andExpect(status().isForbidden());
        for (var ownerId : new UUID[]{UUID.randomUUID(), owner.id()}) {
            if (ownerId.equals(owner.id())) jdbc.update("update users set status = 'INACTIVE' where id = ?", ownerId);
            postCreate(designer, Map.of("key", key(), "name", "Invalid owner", "ownerUserId", ownerId), 400);
        }
    }

    @Test
    void duplicateKeyIsConflictAndDoesNotLeaveExtraDraftsOrAudit() throws Exception {
        var designer = actor("process.design");
        var key = key();
        var original = create(designer, key, "Original");
        postCreate(designer, Map.of("key", key, "name", "Duplicate"), 409);
        assertThat(jdbc.queryForObject("select count(*) from process_definitions where key = ?", Long.class, key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from process_versions where process_definition_id = ?", Long.class, UUID.fromString(id(original)))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where entity_id = ?", Long.class, id(original))).isEqualTo(1);
    }

    @Test
    void concurrentCreatesOfSameKeyHaveExactlyOneWinner() throws Exception {
        var designer = actor("process.design");
        var key = key();
        var barrier = new CyclicBarrier(2);
        Callable<Integer> call = () -> {
            barrier.await(30, java.util.concurrent.TimeUnit.SECONDS);
            return mvc.perform(post(ROOT).header("Authorization", designer.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("key", key, "name", "Concurrent")))).andReturn().getResponse().getStatus();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(call);
            var b = executor.submit(call);
            assertThat(java.util.List.of(a.get(60, java.util.concurrent.TimeUnit.SECONDS), b.get(60, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from process_versions v join process_definitions d on d.id = v.process_definition_id where d.key = ?", Long.class, key)).isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackDefinitionAndDraft() throws Exception {
        var designer = actor("process.design");
        var key = key();
        var useCase = new ProcessDefinitionUseCase(store, transactions,
                () -> new ActorContext(designer.id().toString(), true, Set.of("process.design")),
                (action, type, id, diff) -> { throw new IllegalStateException("audit unavailable"); });
        assertThatThrownBy(() -> useCase.create(new CreateProcess(key, "Rollback", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");
        assertThat(jdbc.queryForObject("select count(*) from process_definitions where key = ?", Long.class, key)).isZero();
    }

    @Test
    void loadsExactStoredXmlStepConfigurationBindingsAndIndependentEngineMapping() throws Exception {
        var admin = actor("process.design", "form.design", "form.publish");
        var created = create(admin, key(), "Config");
        var formVersionId = publishedFixture(admin, created, 12);
        var loaded = json.readTree(mvc.perform(get(versionUrl(created)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version.metadata.versionNumber").value(1))
                .andExpect(jsonPath("$.data.version.engineVersion").value(12))
                .andExpect(jsonPath("$.data.version.formVersionId").value(formVersionId.toString()))
                .andExpect(jsonPath("$.data.version.formBindings.review.formVersionId").value(formVersionId.toString()))
                .andExpect(jsonPath("$.data.version.stepConfig.review.assignment.strategy").value("REQUESTER_MANAGER"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(loaded.get("version").get("bpmnXml").asString()).isEqualTo(XML);
        var publisher = actor("process.publish");
        mvc.perform(get(versionUrl(created)).header("Authorization", publisher.bearer())).andExpect(status().isOk());
        var second = create(admin, key(), "Other");
        mvc.perform(get(ROOT + "/" + id(second) + "/versions/" + vid(created)).header("Authorization", admin.bearer())).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/" + id(created) + "/versions/" + UUID.randomUUID()).header("Authorization", admin.bearer())).andExpect(status().isNotFound());
    }

    @Test
    void historyPaginatesBusinessVersionsAndNeverContainsConfiguration() throws Exception {
        var designer = actor("process.design");
        var created = create(designer, key(), "History");
        for (int version : new int[]{2, 3}) jdbc.update("insert into process_versions (id, created_at, updated_at, revision, process_definition_id, version_number, status, step_config, form_bindings, created_by) values (?, current_timestamp, current_timestamp, 0, ?, ?, 'DRAFT', '{}'::jsonb, '{}'::jsonb, ?)",
                UUID.randomUUID(), UUID.fromString(id(created)), version, designer.id());
        mvc.perform(get(ROOT + "/" + id(created) + "/versions").header("Authorization", designer.bearer()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.items[0].versionNumber").value(3))
                .andExpect(jsonPath("$.data.items[0].bpmnXml").doesNotExist()).andExpect(jsonPath("$.data.items[0].stepConfig").doesNotExist());
        mvc.perform(get(ROOT + "/" + id(created) + "/versions").header("Authorization", designer.bearer()).param("sort", "versionNumber,asc").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].versionNumber").value(2));
        mvc.perform(get(ROOT + "/" + UUID.randomUUID() + "/versions").header("Authorization", designer.bearer())).andExpect(status().isNotFound());
    }

    @Test
    void catalogFiltersBeforeCountAndPaginationAndHidesAdminMetadata() throws Exception {
        var admin = actor("process.design", "form.design", "form.publish");
        var prefix = key();
        var a = create(admin, prefix + "-a", prefix + " A");
        var b = create(admin, prefix + "-b", prefix + " B");
        var c = create(admin, prefix + "-c", prefix + " C");
        var d = create(admin, prefix + "-d", prefix + " D");
        publishedFixture(admin, a, 7);
        publishedFixture(admin, b, 8);
        publishedFixture(admin, c, 9);
        jdbc.update("update process_definitions set status = 'INACTIVE' where id = ?", UUID.fromString(id(b)));
        jdbc.update("update process_versions set status = 'RETIRED' where id = ?", UUID.fromString(vid(c)));
        for (var permission : new String[]{"process.catalog.read", "process.start"}) {
            var employee = actor(permission);
            var response = mvc.perform(get(ROOT).header("Authorization", employee.bearer()).param("sort", "key,asc"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var page = json.readTree(response).get("data");
            assertThat(page.get("items").toString()).contains(id(a)).doesNotContain(id(b), id(c), id(d));
            assertThat(response).doesNotContain("bpmnXml", "engineDefinitionId", "ownerUserId", "stepConfig", "formBindings");
            var total = page.get("totalElements").asLong();
            mvc.perform(get(ROOT).header("Authorization", employee.bearer()).param("size", "1").param("page", Long.toString(total)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(total)).andExpect(jsonPath("$.data.items").isEmpty());
        }
        // A PUBLISHED version is not startable without the explicit active-version pointer.
        jdbc.update("update process_definitions set active_process_version_id = null where id = ?", UUID.fromString(id(a)));
        var reader = actor("process.start");
        assertThat(mvc.perform(get(ROOT).header("Authorization", reader.bearer())).andReturn().getResponse().getContentAsString()).doesNotContain(id(a));
        var monitor = actor("process.monitor.all");
        assertThat(mvc.perform(get(ROOT).header("Authorization", monitor.bearer()).param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains(id(a), id(b), id(c), id(d)).doesNotContain("stepConfig");
    }

    @Test
    void metadataAndCreationEnforcePermissionsByIdListAndAfterRevocation() throws Exception {
        var designer = actor("process.design");
        var created = create(designer, key(), "Scope");
        var urls = new String[]{ROOT + "/" + id(created), ROOT + "/" + id(created) + "/versions", versionUrl(created)};
        for (var url : urls) mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        for (var permission : new String[]{"record.create", "process.catalog.read", "process.start", "process.monitor.all", "process.monitor.owned"}) {
            var employee = actor(permission);
            for (var url : urls) mvc.perform(get(url).header("Authorization", employee.bearer())).andExpect(status().isForbidden());
            postCreate(employee, Map.of("key", key(), "name", "Forbidden"), 403);
        }
        var publisher = actor("process.publish");
        for (var url : urls) mvc.perform(get(url).header("Authorization", publisher.bearer())).andExpect(status().isOk());
        postCreate(publisher, Map.of("key", key(), "name", "Forbidden"), 403);
        jdbc.update("delete from user_roles where user_id = ?", designer.id());
        for (var url : urls) mvc.perform(get(url).header("Authorization", designer.bearer())).andExpect(status().isForbidden());
        mvc.perform(get(ROOT).header("Authorization", designer.bearer())).andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidCreationAndQueryInputs() throws Exception {
        var designer = actor("process.design");
        for (var bad : new String[]{"", "Purchase", "space key", "a".repeat(101)}) postCreate(designer, Map.of("key", bad, "name", "Test"), 400);
        for (var bad : new String[]{"", "  ", "a".repeat(201)}) postCreate(designer, Map.of("key", key(), "name", bad), 400);
        postCreate(designer, Map.of("key", key(), "name", "Test", "description", "a".repeat(4001)), 400);
        var created = create(designer, key(), "Queries");
        for (var pair : new String[][]{{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"page", "2147483647"}, {"sort", "ownerUserId,asc"}, {"sort", "name;delete,asc"}})
            mvc.perform(get(ROOT).header("Authorization", designer.bearer()).param(pair[0], pair[1])).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/" + id(created) + "/versions").header("Authorization", designer.bearer()).param("sort", "name,asc")).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/bad-id").header("Authorization", designer.bearer())).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/" + UUID.randomUUID()).header("Authorization", designer.bearer())).andExpect(status().isNotFound());
    }

    /** Test-only mapping, not an API to bypass P4.5/P4.7. No fixture deploy reaches Flowable. */
    @SuppressWarnings("unchecked")
    private UUID publishedFixture(TestActor admin, JsonNode process, int engineVersion) throws Exception {
        Map<String, Object> body;
        try (var stream = getClass().getResourceAsStream("/forms/purchase-request.v1.json")) { body = json.readValue(stream, Map.class); }
        body.put("key", key());
        var form = json.readTree(mvc.perform(post("/api/v1/form-definitions").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        var formId = form.get("definition").get("id").asString();
        var formVersionId = UUID.fromString(form.get("version").get("id").asString());
        mvc.perform(post("/api/v1/form-definitions/" + formId + "/publish").header("Authorization", admin.bearer()).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId", formVersionId, "expectedRevision", 0)))).andExpect(status().isOk());
        jdbc.update("update process_versions set status = 'PUBLISHED', bpmn_xml = ?, step_config = ?::jsonb, form_bindings = ?::jsonb, form_version_id = ?, engine_deployment_id = ?, engine_definition_id = ?, engine_version = ?, artifact_checksum = ?, published_at = current_timestamp, published_by = ? where id = ?",
                XML, json.writeValueAsString(Map.of("review", Map.of("assignment", Map.of("strategy", "REQUESTER_MANAGER"), "actions", java.util.List.of("APPROVE", "REJECT")))),
                json.writeValueAsString(Map.of("review", Map.of("formVersionId", formVersionId))), formVersionId,
                "fixture-" + UUID.randomUUID(), "fixture-" + UUID.randomUUID(), engineVersion, "a".repeat(64), admin.id(), UUID.fromString(vid(process)));
        jdbc.update("update process_definitions set active_process_version_id = ? where id = ?", UUID.fromString(vid(process)), UUID.fromString(id(process)));
        return formVersionId;
    }

    private JsonNode create(TestActor actor, String key, String name) throws Exception { return postCreate(actor, Map.of("key", key, "name", name), 200); }
    private JsonNode postCreate(TestActor actor, Map<String, Object> body, int status) throws Exception {
        return json.readTree(mvc.perform(post(ROOT).header("Authorization", actor.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().is(status)).andReturn().getResponse().getContentAsString()).get("data");
    }
    private String id(JsonNode p) { return p.get("definition").get("id").asString(); }
    private String vid(JsonNode p) { return p.get("version").get("metadata").get("id").asString(); }
    private String versionUrl(JsonNode p) { return ROOT + "/" + id(p) + "/versions/" + vid(p); }
    private String key() { return "p41-" + UUID.randomUUID(); }
    private record TestActor(UUID id, String token) { String bearer() { return "Bearer " + token; } }
    private TestActor actor(String... permissions) throws Exception {
        var user = accounts.create(new NewUserAccount(key(), "P4.1 Tester", passwords.hash(PASSWORD), AccountStatus.ACTIVE, false));
        var role = UUID.randomUUID();
        jdbc.update("insert into roles (id, created_at, updated_at, revision, code, name, status) values (?, current_timestamp, current_timestamp, 0, ?, 'P4.1', 'ACTIVE')", role, key());
        jdbc.update("insert into user_roles (id, created_at, updated_at, revision, user_id, role_id) values (?, current_timestamp, current_timestamp, 0, ?, ?)", UUID.randomUUID(), user.id(), role);
        for (var permission : permissions) jdbc.update("insert into role_permissions (id, created_at, updated_at, revision, role_id, permission_id) values (?, current_timestamp, current_timestamp, 0, ?, ?)",
                UUID.randomUUID(), role, jdbc.queryForObject("select id from permissions where code = ?", UUID.class, permission));
        var token = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("username", user.username(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data").get("accessToken").asString();
        return new TestActor(user.id(), token);
    }
}
