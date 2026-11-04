package com.x.bpm.form.api;

import com.x.bpm.authorization.domain.AuthorizationPolicy;
import com.x.bpm.form.application.*;
import com.x.bpm.form.domain.FormVersion;
import com.x.bpm.identity.api.AuthController;
import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.NewUserAccount;
import com.x.bpm.identity.application.PasswordHashPort;
import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.support.IsolatedPostgresTest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IsolatedPostgresTest
@AutoConfigureMockMvc
class FormVersionLifecycleIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private AuthAccountPort accounts;
    @Autowired private PasswordHashPort passwordHashes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private FormStore store;
    @Autowired private FormTransactionRunner formTransactions;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entities;

    private record Actor(UUID id, String token) { }
    private record Fixture(Actor actor, FormArtifact artifact) { }

    @Test
    void publishRetriesReturnSameVersionAndRejectReusedKeys() throws Exception {
        var fixture = fixture();
        var key = UUID.randomUUID();
        var first = mutation(fixture, "publish", key, 0);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        var published = artifact(first);
        assertThat(published.version().status()).isEqualTo(FormVersion.Status.PUBLISHED);
        assertThat(published.version().publishedBy()).isEqualTo(fixture.actor().id());
        assertThat(published.version().publishedAt()).isNotNull();
        assertThat(artifact(mutation(fixture, "publish", key, 0))).isEqualTo(published);
        assertThat(mutation(fixture, "publish", UUID.randomUUID(), 0).getResponse().getStatus()).isEqualTo(409);
        var reused = mutation(fixture, "publish", key, 1);
        assertThat(reused.getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(reused.getResponse().getContentAsString()).get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(mutation(fixture, "clone", key, 1).getResponse().getStatus()).isEqualTo(409);
        assertThat(count("form_versions", "form_definition_id", fixture.artifact().definition().id())).isEqualTo(1);
        assertThat(audits(fixture, "FORM_PUBLISHED")).isEqualTo(1);
        assertThat(count("form_version_operations", "form_definition_id", fixture.artifact().definition().id())).isEqualTo(1);
    }

    @Test
    void cloneHasNewIdNumberAndIndependentDraftWhilePublishedSourceRemainsUnchanged() throws Exception {
        var fixture = fixture();
        var published = artifact(mutation(fixture, "publish", UUID.randomUUID(), 0));
        var source = new Fixture(fixture.actor(), published);
        var key = UUID.randomUUID();
        var cloned = artifact(mutation(source, "clone", key, published.version().revision()));
        assertThat(cloned.version().id()).isNotEqualTo(published.version().id());
        assertThat(cloned.version().versionNumber()).isEqualTo(2);
        assertThat(cloned.version().status()).isEqualTo(FormVersion.Status.DRAFT);
        assertThat(cloned.version().revision()).isZero();
        assertThat(cloned.version().publishedAt()).isNull();
        assertThat(cloned.version().publishedBy()).isNull();
        assertThat(cloned.version().schema()).isEqualTo(published.version().schema());
        assertThat(cloned.version().uiSchema()).isEqualTo(published.version().uiSchema());
        assertThat(artifact(mutation(source, "clone", key, published.version().revision()))).isEqualTo(cloned);
        var doc = new LinkedHashMap<>(cloned.version().schema()); doc.put("title", "Clone edited independently");
        assertThat(save(new Fixture(fixture.actor(), cloned), doc, 0).getResponse().getStatus()).isEqualTo(200);
        assertThat(store.findVersion(published.definition().id(), published.version().id()).orElseThrow()).isEqualTo(published);
        assertThat(save(source, doc, published.version().revision()).getResponse().getStatus()).isEqualTo(409);
        assertThat(audits(fixture, "FORM_DRAFT_CLONED")).isEqualTo(1);
    }

    @Test
    void designerPublisherAndEmployeeHaveSeparateCapabilitiesIncludingReplay() throws Exception {
        var fixture = fixture();
        var publisher = actor("form.publish");
        var designer = actor("form.design");
        var employee = actor();
        var source = fixture.artifact();
        var path = "/api/v1/form-definitions/" + source.definition().id();
        mvc.perform(get(path + "/versions/" + source.version().id()).header("Authorization", "Bearer " + publisher.token()))
                .andExpect(status().isOk());
        assertThat(mutation(new Fixture(designer, source), "publish", UUID.randomUUID(), 0).getResponse().getStatus()).isEqualTo(403);
        assertThat(mutation(new Fixture(publisher, source), "clone", UUID.randomUUID(), 0).getResponse().getStatus()).isEqualTo(403);
        assertThat(save(new Fixture(publisher, source), source.version().schema(), 0).getResponse().getStatus()).isEqualTo(403);
        assertThat(mutation(new Fixture(employee, source), "publish", UUID.randomUUID(), 0).getResponse().getStatus()).isEqualTo(403);
        mvc.perform(post(path + "/validate").header("Authorization", "Bearer " + employee.token())
                .contentType(MediaType.APPLICATION_JSON).content(command(source, 0))).andExpect(status().isForbidden());
        mvc.perform(post(path + "/publish").contentType(MediaType.APPLICATION_JSON).content(command(source, 0)))
                .andExpect(status().isUnauthorized());
        var key = UUID.randomUUID();
        assertThat(mutation(new Fixture(publisher, source), "publish", key, 0).getResponse().getStatus()).isEqualTo(200);
        jdbc.update("delete from role_permissions where role_id in (select role_id from user_roles where user_id = ?)", publisher.id());
        assertThat(mutation(new Fixture(publisher, source), "publish", key, 0).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void validateAndPublishRejectStaleMalformedOrInvalidSavedSchema() throws Exception {
        var fixture = fixture();
        var path = "/api/v1/form-definitions/" + fixture.artifact().definition().id();
        mvc.perform(post(path + "/validate").header("Authorization", "Bearer " + fixture.actor().token())
                .contentType(MediaType.APPLICATION_JSON).content(command(fixture.artifact(), 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true));
        mvc.perform(post(path + "/validate").header("Authorization", "Bearer " + fixture.actor().token())
                .contentType(MediaType.APPLICATION_JSON).content(command(fixture.artifact(), 1))).andExpect(status().isConflict());
        mvc.perform(post(path + "/publish").header("Authorization", "Bearer " + fixture.actor().token())
                .contentType(MediaType.APPLICATION_JSON).content(command(fixture.artifact(), 0))).andExpect(status().isBadRequest());
        jdbc.update("update form_versions set schema_json = '{}'::jsonb where id = ?", fixture.artifact().version().id());
        mvc.perform(post(path + "/validate").header("Authorization", "Bearer " + fixture.actor().token())
                .contentType(MediaType.APPLICATION_JSON).content(command(fixture.artifact(), 0))).andExpect(status().isUnprocessableContent());
        assertThat(mutation(fixture, "publish", UUID.randomUUID(), 0).getResponse().getStatus()).isEqualTo(422);
        assertThat(count("form_version_operations", "form_definition_id", fixture.artifact().definition().id())).isZero();
        assertThat(audits(fixture, "FORM_PUBLISHED")).isZero();
    }

    @Test
    void sameKeyConcurrentPublishReplaysExactlyOneTransitionAndAudit() throws Exception {
        var fixture = fixture(); var key = UUID.randomUUID();
        var results = race(() -> uncheckedMutation(fixture, "publish", key, 0), () -> uncheckedMutation(fixture, "publish", key, 0));
        assertThat(results).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        assertThat(artifact(results.get(0))).isEqualTo(artifact(results.get(1)));
        assertThat(audits(fixture, "FORM_PUBLISHED")).isEqualTo(1);
    }

    @Test
    void differentKeyConcurrentPublishHasOneWinner() throws Exception {
        var fixture = fixture();
        var results = race(() -> uncheckedMutation(fixture, "publish", UUID.randomUUID(), 0),
                () -> uncheckedMutation(fixture, "publish", UUID.randomUUID(), 0));
        assertThat(results.stream().map(result -> result.getResponse().getStatus())).containsExactlyInAnyOrder(200, 409);
        assertThat(audits(fixture, "FORM_PUBLISHED")).isEqualTo(1);
        assertThat(count("form_versions", "form_definition_id", fixture.artifact().definition().id())).isEqualTo(1);
    }

    @Test
    void concurrentSaveWithSameRevisionCannotSilentlyOverwrite() throws Exception {
        var fixture = fixture();
        var secondDesigner = new Fixture(actor("form.design"), fixture.artifact());
        var first = new LinkedHashMap<>(fixture.artifact().version().schema()); first.put("title", "First");
        var second = new LinkedHashMap<>(fixture.artifact().version().schema()); second.put("title", "Second");
        var results = race(() -> uncheckedSave(fixture, first), () -> uncheckedSave(secondDesigner, second));
        assertThat(results.stream().map(result -> result.getResponse().getStatus())).containsExactlyInAnyOrder(200, 409);
        assertThat(audits(fixture, "FORM_DRAFT_SAVED")).isEqualTo(1);
    }

    @Test
    void saveVersusPublishRaceProtectsTheWinningRevision() throws Exception {
        var fixture = fixture();
        var changed = new LinkedHashMap<>(fixture.artifact().version().schema()); changed.put("title", "Changed");
        var results = race(() -> uncheckedSave(fixture, changed), () -> uncheckedMutation(fixture, "publish", UUID.randomUUID(), 0));
        assertThat(results.stream().map(result -> result.getResponse().getStatus())).containsExactlyInAnyOrder(200, 409);
        var stored = store.findVersion(fixture.artifact().definition().id(), fixture.artifact().version().id()).orElseThrow();
        if (stored.version().status() == FormVersion.Status.PUBLISHED)
            assertThat(stored.version().schema()).isEqualTo(fixture.artifact().version().schema());
        else assertThat(stored.version().schema().get("title")).isEqualTo("Changed");
    }

    @Test
    void concurrentClonesAllocateDistinctNumbersAndRetrySameKeyDoesNotCloneAgain() throws Exception {
        var fixture = fixture(); var key = UUID.randomUUID();
        var same = race(() -> uncheckedMutation(fixture, "clone", key, 0), () -> uncheckedMutation(fixture, "clone", key, 0));
        assertThat(same).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        assertThat(artifact(same.get(0)).version().id()).isEqualTo(artifact(same.get(1)).version().id());
        var different = race(() -> uncheckedMutation(fixture, "clone", UUID.randomUUID(), 0),
                () -> uncheckedMutation(fixture, "clone", UUID.randomUUID(), 0));
        assertThat(different).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        assertThat(List.of(artifact(different.get(0)).version().versionNumber(), artifact(different.get(1)).version().versionNumber()))
                .containsExactlyInAnyOrder(3, 4);
        assertThat(count("form_versions", "form_definition_id", fixture.artifact().definition().id())).isEqualTo(4);
    }

    @Test
    void auditFailureRollsBackPublicationAndReceiptThenSameKeyCanRetry() throws Exception {
        var fixture = fixture(); var key = UUID.randomUUID();
        var useCase = new FormDefinitionUseCase(store, formTransactions,
                () -> new ActorContext(fixture.actor().id().toString(), true, Set.of("form.publish")),
                new AuthorizationPolicy(), (a,b,c,d) -> { throw new IllegalStateException("audit failure fixture"); });
        assertThatThrownBy(() -> useCase.mutateVersion(fixture.artifact().definition().id(),
                new FormVersionCommand(fixture.artifact().version().id(), 0L), key, FormVersionOperation.PUBLISH))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.findVersion(fixture.artifact().definition().id(), fixture.artifact().version().id()).orElseThrow())
                .isEqualTo(fixture.artifact());
        assertThat(count("form_version_operations", "form_definition_id", fixture.artifact().definition().id())).isZero();
        assertThat(mutation(fixture, "publish", key, 0).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void entityGuardRejectsDirectPublishedContentMutation() throws Exception {
        var fixture = fixture();
        var published = artifact(mutation(fixture, "publish", UUID.randomUUID(), 0));
        List<java.util.function.Consumer<com.x.bpm.form.adapter.persistence.FormVersionEntity>> changes = List.of(
                entity -> entity.getSchemaJson().put("title", "Forbidden rewrite"),
                entity -> entity.setUiSchemaJson(Map.of()),
                entity -> entity.setStatus(com.x.bpm.form.adapter.persistence.FormVersionEntity.Status.DRAFT),
                entity -> entity.setPublishedAt(entity.getPublishedAt().plusSeconds(1)),
                entity -> entity.setVersionNumber(99));
        for (var change : changes) {
            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                var entity = entities.find(com.x.bpm.form.adapter.persistence.FormVersionEntity.class, published.version().id());
                change.accept(entity);
                entities.flush();
            })).isInstanceOf(RuntimeException.class);
            assertThat(store.findVersion(published.definition().id(), published.version().id()).orElseThrow()).isEqualTo(published);
        }
    }

    @Test
    void keysCannotBeReusedAcrossDefinitionsButAreScopedToActor() throws Exception {
        var first = fixture();
        var second = fixture(first.actor());
        var key = UUID.randomUUID();
        assertThat(mutation(first, "publish", key, 0).getResponse().getStatus()).isEqualTo(200);
        var reused = mutation(second, "publish", key, 0);
        assertThat(reused.getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(reused.getResponse().getContentAsString()).get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(store.findVersion(second.artifact().definition().id(), second.artifact().version().id()).orElseThrow())
                .isEqualTo(second.artifact());
        var anotherActor = actor("form.publish");
        assertThat(mutation(new Fixture(anotherActor, second.artifact()), "publish", key, 0).getResponse().getStatus()).isEqualTo(200);
    }

    private Fixture fixture() throws Exception {
        return fixture(actor("form.design", "form.publish"));
    }
    private Fixture fixture(Actor actor) throws Exception {
        Map<?, ?> source;
        try (var stream = getClass().getResourceAsStream("/forms/purchase-request.v1.json")) { source = json.readValue(stream, Map.class); }
        var payload = new LinkedHashMap<Object,Object>(source); payload.put("key", "lifecycle-" + UUID.randomUUID());
        var result = mvc.perform(post("/api/v1/form-definitions").header("Authorization", "Bearer " + actor.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload))).andExpect(status().isOk()).andReturn();
        return new Fixture(actor, artifact(result));
    }
    private Actor actor(String... permissions) throws Exception {
        var password = "correct horse battery staple";
        var account = accounts.create(new NewUserAccount("p35-" + UUID.randomUUID(), "Lifecycle User",
                passwordHashes.hash(password), AccountStatus.ACTIVE, false));
        if (permissions.length > 0) grantPermissions(account.id(), permissions);
        return new Actor(account.id(), login(account.username(), password));
    }
    private MvcResult mutation(Fixture fixture, String operation, UUID key, long revision) throws Exception {
        return mvc.perform(post("/api/v1/form-definitions/" + fixture.artifact().definition().id() + "/" + operation)
                .header("Authorization", "Bearer " + fixture.actor().token()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(command(fixture.artifact(), revision))).andReturn();
    }
    private MvcResult save(Fixture fixture, Map<String,Object> schema, long revision) throws Exception {
        return mvc.perform(put("/api/v1/form-definitions/" + fixture.artifact().definition().id() + "/draft")
                .header("Authorization", "Bearer " + fixture.actor().token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(new SaveFormDraft(fixture.artifact().version().id(), revision,
                        schema, fixture.artifact().version().uiSchema())))).andReturn();
    }
    private MvcResult uncheckedMutation(Fixture fixture, String operation, UUID key, long revision) {
        try { return mutation(fixture, operation, key, revision); } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private MvcResult uncheckedSave(Fixture fixture, Map<String,Object> schema) {
        try { return save(fixture, schema, 0); } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private List<MvcResult> race(Supplier<MvcResult> first, Supplier<MvcResult> second) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { start.await(); return first.get(); });
            var two = executor.submit(() -> { start.await(); return second.get(); });
            start.countDown();
            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        }
    }
    private String command(FormArtifact artifact, long revision) {
        return json.writeValueAsString(new FormVersionCommand(artifact.version().id(), revision));
    }
    private FormArtifact artifact(MvcResult result) {
        return json.readValue(json.readTree(result.getResponse().getContentAsByteArray()).get("data").toString(), FormArtifact.class);
    }
    private long count(String table, String column, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Long.class, id);
    }
    private long audits(Fixture fixture, String action) {
        return jdbc.queryForObject("select count(*) from audit_events where entity_id = ? and action = ?", Long.class,
                fixture.artifact().definition().id().toString(), action);
    }

    private String login(String username, String password) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(
                                new AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asString();
    }

    private void grantPermissions(UUID userId, String... permissionCodes) {
        var roleId = UUID.randomUUID();
        jdbc.update("""
                insert into roles (id, created_at, updated_at, revision, code, name, status)
                values (?, current_timestamp, current_timestamp, 0, ?, ?, 'ACTIVE')
                """, roleId, "p35-role-" + roleId, "Lifecycle Role");
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
