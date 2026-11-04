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
    @Autowired ProcessPublicationStore publications;
    @Autowired ProcessValidationPort validator;
    @Autowired com.x.bpm.process.application.engine.ProcessEnginePort engine;
    @Autowired org.flowable.engine.RepositoryService engineRepository;
    @Autowired org.flowable.engine.TaskService engineTasks;
    @Autowired ProcessLifecycleStore lifecycleStore;

    @Test
    void lifecycleClonesDuplicatesActivatesAndRetiresWithoutInterruptingPinnedInstances() throws Exception {
        var admin=actor("process.design","process.publish","form.design","form.publish","process.start");
        var draft=validDraft(admin);var v1=publish(admin,draft);
        String engineId=v1.get("version").get("engineDefinitionId").asString();
        var running=engine.start(engineId,"p48-"+UUID.randomUUID(),Map.of());
        UUID cloneKey=UUID.randomUUID();var clone=mutate(admin,v1,"clone",cloneKey,Map.of(),200);
        assertThat(mutate(admin,v1,"clone",cloneKey,Map.of(),200)).isEqualTo(clone);
        assertThat(clone.get("version").get("metadata").get("versionNumber").asInt()).isEqualTo(2);
        assertThat(clone.get("version").get("metadata").get("status").asString()).isEqualTo("DRAFT");
        for(String field:new String[]{"bpmnXml","stepConfig","formBindings","formVersionId"})assertThat(clone.get("version").get(field)).isEqualTo(v1.get("version").get(field));
        assertThat(clone.get("version").get("deploymentXml").isNull()).isTrue();
        assertThat(clone.get("version").get("engineDefinitionId").isNull()).isTrue();
        String newKey=key();UUID duplicateKey=UUID.randomUUID();
        var duplicate=mutate(admin,v1,"duplicate",duplicateKey,Map.of("key",newKey,"name","Independent copy"),200);
        assertThat(mutate(admin,v1,"duplicate",duplicateKey,Map.of("key",newKey,"name","Independent copy"),200)).isEqualTo(duplicate);
        mutate(admin,v1,"duplicate",duplicateKey,Map.of("key",newKey,"name","Changed request"),409);
        String copiedXml=duplicate.get("version").get("bpmnXml").asString();
        assertThat(copiedXml).contains("id=\""+newKey+"\"","bpmnElement=\""+newKey+"\"","id=\"review\"");
        assertThat(duplicate.get("version").get("metadata").get("versionNumber").asInt()).isEqualTo(1);
        assertThat(duplicate.get("definition").get("activeProcessVersionId").isNull()).isTrue();
        mvc.perform(post(ROOT+"/"+id(duplicate)+"/validate").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new ProcessModelInput(copiedXml,json.convertValue(duplicate.get("version").get("stepConfig"),Map.class),json.convertValue(duplicate.get("version").get("formBindings"),Map.class))))).andExpect(status().isOk());
        var v2=publish(admin,clone);
        var chosen=mutate(admin,v1,"activate",UUID.randomUUID(),Map.of(),200);
        assertThat(chosen.get("definition").get("activeProcessVersionId").asString()).isEqualTo(vid(v1));
        mvc.perform(get(ROOT+"/startable").header("Authorization",admin.bearer()).param("q",draft.get("definition").get("key").asString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].versionId").value(vid(v1)));
        UUID retireKey=UUID.randomUUID();var retired=mutate(admin,v1,"retire",retireKey,Map.of(),200);
        assertThat(mutate(admin,v1,"retire",retireKey,Map.of(),200)).isEqualTo(retired);
        assertThat(retired.get("definition").get("activeProcessVersionId").isNull()).isTrue();
        assertThat(retired.get("version").get("metadata").get("status").asString()).isEqualTo("RETIRED");
        assertThat(retired.get("version").get("deploymentXml")).isEqualTo(v1.get("version").get("deploymentXml"));
        mvc.perform(get(ROOT+"/startable").header("Authorization",admin.bearer()).param("q",draft.get("definition").get("key").asString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mutate(admin,retired,"activate",UUID.randomUUID(),Map.of(),409);
        assertThat(engine.findRunningInstance(running.instanceId()).orElseThrow().definitionId()).isEqualTo(engineId);
        assertThat(engine.findRunningInstance(running.instanceId()).orElseThrow().suspended()).isFalse();
        var task=engineTasks.createTaskQuery().processInstanceId(running.instanceId()).singleResult();
        engineTasks.complete(task.getId(),Map.of("bpmOutcome","APPROVE"));
        assertThat(engine.findHistory(running.instanceId()).orElseThrow().endActivityId()).isEqualTo("end");
        assertThat(engineRepository.createProcessDefinitionQuery().processDefinitionId(engineId).count()).isEqualTo(1);
        var activeV2=mutate(admin,v2,"activate",UUID.randomUUID(),Map.of(),200);
        assertThat(activeV2.get("definition").get("activeProcessVersionId").asString()).isEqualTo(vid(v2));
        for(var event:new String[]{"PROCESS_CLONE","PROCESS_DUPLICATE","PROCESS_RETIRE"})assertThat(jdbc.queryForObject("select count(*) from audit_events where action=? and entity_id=?",Long.class,event,id(v1))).isEqualTo(1);
    }

    @Test
    void managementAndStartCatalogFilterBeforeCountAndNeverGrantManagementToStartOrMonitor() throws Exception {
        var admin=actor("process.design","process.publish","form.design","form.publish");var draft=validDraft(admin);
        String q=draft.get("definition").get("key").asString();
        mvc.perform(get(ROOT+"/management").header("Authorization",admin.bearer()).param("q",q).param("versionStatus","DRAFT").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.items[0].id").value(id(draft)));
        mvc.perform(get(ROOT+"/management").header("Authorization",admin.bearer()).param("q",q).param("versionStatus","PUBLISHED")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        var published=publish(admin,draft);
        mvc.perform(get(ROOT+"/management").header("Authorization",admin.bearer()).param("q",q).param("versionStatus","DRAFT")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get(ROOT+"/management").header("Authorization",admin.bearer()).param("q",q).param("versionStatus","PUBLISHED").param("page","1").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get(ROOT+"/management").header("Authorization",admin.bearer()).param("q","%" )).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        for(var permission:new String[]{"process.start","process.catalog.read","process.monitor.all","process.monitor.owned"}){
            var reader=actor(permission);
            mvc.perform(get(ROOT+"/management").header("Authorization",reader.bearer())).andExpect(status().isForbidden());
            mutate(reader,published,"clone",UUID.randomUUID(),Map.of(),403);
            mutate(reader,published,"retire",UUID.randomUUID(),Map.of(),403);
            if(permission.equals("process.start")||permission.equals("process.catalog.read")){
                String body=mvc.perform(get(ROOT+"/startable").header("Authorization",reader.bearer()).param("q",q)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1)).andReturn().getResponse().getContentAsString();
                assertThat(body).doesNotContain("bpmnXml","deploymentXml","engineDefinitionId","ownerUserId","stepConfig","formBindings");
            } else mvc.perform(get(ROOT+"/startable").header("Authorization",reader.bearer())).andExpect(status().isForbidden());
        }
        mvc.perform(get(ROOT+"/startable").header("Authorization",admin.bearer())).andExpect(status().isForbidden());
        for(var pair:new String[][]{{"q","x".repeat(201)},{"versionStatus","INVALID"},{"sort","ownerUserId,asc"},{"size","101"}})
            mvc.perform(get(ROOT+"/management").header("Authorization",admin.bearer()).param(pair[0],pair[1])).andExpect(status().isBadRequest());
        mutate(actor("process.publish"),published,"clone",UUID.randomUUID(),Map.of(),403);
        mutate(actor("process.design"),published,"activate",UUID.randomUUID(),Map.of(),403);
    }

    @Test
    void concurrentCloneRetriesAndAuditFailureDoNotLeaveExtraVersionsOrReceipts() throws Exception {
        var admin=actor("process.design","process.publish","form.design","form.publish");var source=publish(admin,validDraft(admin));
        UUID key=UUID.randomUUID();var barrier=new CyclicBarrier(2);
        Callable<JsonNode> clone=()->{barrier.await(30,java.util.concurrent.TimeUnit.SECONDS);return mutate(admin,source,"clone",key,Map.of(),200);};
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(clone);var b=pool.submit(clone);assertThat(a.get(60,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(b.get(60,java.util.concurrent.TimeUnit.SECONDS));}
        var useCase=new ProcessLifecycleUseCase(store,publications,lifecycleStore,transactions,()->new ActorContext(admin.id().toString(),true,Set.of("process.design")),(action,type,id,diff)->{throw new IllegalStateException("lifecycle audit failure");},validator);
        UUID failingKey=UUID.randomUUID();var command=new ProcessLifecycleCommand(UUID.fromString(vid(source)),source.get("version").get("metadata").get("revision").asLong(),null,null,null,null);
        assertThatThrownBy(()->useCase.mutate(UUID.fromString(id(source)),command,failingKey,ProcessLifecycleOperation.CLONE)).isInstanceOf(IllegalStateException.class).hasMessage("lifecycle audit failure");
        assertThat(jdbc.queryForObject("select count(*) from process_versions where process_definition_id=?",Long.class,UUID.fromString(id(source)))).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from process_lifecycle_receipts where request_key=?",Long.class,failingKey)).isZero();
        var retried=mutate(admin,source,"clone",failingKey,Map.of(),200);assertThat(retried.get("version").get("metadata").get("versionNumber").asInt()).isEqualTo(3);
        var stale=new java.util.LinkedHashMap<String,Object>();stale.put("versionId",vid(source));stale.put("expectedRevision",0);
        mvc.perform(post(ROOT+"/"+id(source)+"/clone").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(stale))).andExpect(status().isConflict());
    }

    private JsonNode mutate(TestActor actor,JsonNode source,String operation,UUID key,Map<String,Object> extra,int status) throws Exception {
        var body=new java.util.LinkedHashMap<String,Object>(extra);body.put("versionId",vid(source));body.put("expectedRevision",source.get("version").get("metadata").get("revision").asLong());
        return json.readTree(mvc.perform(post(ROOT+"/"+id(source)+"/"+operation).header("Authorization",actor.bearer()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().is(status)).andReturn().getResponse().getContentAsString()).get("data");
    }
    private JsonNode publish(TestActor actor,JsonNode source) throws Exception {
        return json.readTree(mvc.perform(post(ROOT+"/"+id(source)+"/publish").header("Authorization",actor.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",vid(source),"expectedRevision",source.get("version").get("metadata").get("revision").asLong())))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    void publishesOnceWithConcurrentRetryFreezesExactArtifactAndRunsCompiledOutcome() throws Exception {
        var admin=actor("process.design","process.publish","form.design","form.publish");
        var saved=validDraft(admin);
        String url=ROOT+"/"+id(saved)+"/publish";
        UUID requestKey=UUID.randomUUID();
        var body=json.writeValueAsBytes(Map.of("versionId",vid(saved),"expectedRevision",1));
        long before=jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class);
        var barrier=new CyclicBarrier(2);
        Callable<JsonNode> publish=()->{barrier.await(30,java.util.concurrent.TimeUnit.SECONDS);return json.readTree(mvc.perform(post(url).header("Authorization",admin.bearer()).header("Idempotency-Key",requestKey).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");};
        JsonNode published;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(publish);var b=pool.submit(publish);
            published=a.get(60,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(b.get(60,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(published);
        }
        var version=published.get("version");
        assertThat(version.get("metadata").get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(published.get("definition").get("activeProcessVersionId").asString()).isEqualTo(vid(saved));
        assertThat(version.get("bpmnXml")).isEqualTo(saved.get("version").get("bpmnXml"));
        assertThat(version.get("stepConfig")).isEqualTo(saved.get("version").get("stepConfig"));
        assertThat(version.get("formBindings")).isEqualTo(saved.get("version").get("formBindings"));
        String xml=version.get("deploymentXml").asString();
        assertThat(xml).contains("conditionExpression","bpmConditions.outcome");
        assertThat(version.get("artifactChecksum").asString()).isEqualTo(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        try(var resource=engineRepository.getResourceAsStream(version.get("engineDeploymentId").asString(),saved.get("definition").get("key").asString()+".bpmn20.xml")) {
            assertThat(new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(xml);
        }
        assertThat(jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class)).isEqualTo(before+1);
        assertThat(jdbc.queryForObject("select count(*) from process_publications where process_definition_id=?",Long.class,UUID.fromString(id(saved)))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action='PROCESS_PUBLISHED' and entity_id=?",Long.class,id(saved))).isEqualTo(1);
        mvc.perform(post(url).header("Authorization",admin.bearer()).header("Idempotency-Key",requestKey).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",vid(saved),"expectedRevision",2)))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(post(url).header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        var instance=engine.start(version.get("engineDefinitionId").asString(),"p47-"+UUID.randomUUID(),Map.of());
        var task=engineTasks.createTaskQuery().processInstanceId(instance.instanceId()).singleResult();
        assertThat(task.getTaskDefinitionKey()).isEqualTo("review");
        engineTasks.complete(task.getId(),Map.of("bpmOutcome","APPROVE"));
        assertThat(engine.findRunningInstance(instance.instanceId())).isEmpty();
        assertThat(engine.findHistory(instance.instanceId()).orElseThrow().endActivityId()).isEqualTo("end");
    }

    @Test
    void competingDifferentPublishKeysHaveOneWinnerAndSecurityValidationPrecedeEngine() throws Exception {
        var admin=actor("process.design","process.publish","form.design","form.publish");
        var saved=validDraft(admin);String url=ROOT+"/"+id(saved)+"/publish";
        byte[] body=json.writeValueAsBytes(Map.of("versionId",vid(saved),"expectedRevision",1));
        mvc.perform(post(url).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization",actor("process.design").bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(url).header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post(url).header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",vid(saved),"expectedRevision",0)))).andExpect(status().isConflict());
        var barrier=new CyclicBarrier(2);
        Callable<Integer> publish=()->{barrier.await(30,java.util.concurrent.TimeUnit.SECONDS);return mvc.perform(post(url).header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();};
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(publish);var b=pool.submit(publish);assertThat(java.util.List.of(a.get(60,java.util.concurrent.TimeUnit.SECONDS),b.get(60,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}
        var invalid=create(admin,key(),"Empty draft");
        long deployments=jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class);
        mvc.perform(post(ROOT+"/"+id(invalid)+"/publish").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",vid(invalid),"expectedRevision",0)))).andExpect(status().isUnprocessableContent());
        assertThat(jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class)).isEqualTo(deployments);
    }

    @Test
    void publicationAuditFailureRollsBackEngineFrozenStatePointerAndReceiptThenAllowsRetry() throws Exception {
        var admin=actor("process.design","process.publish","form.design","form.publish");var saved=validDraft(admin);
        long before=jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class);
        UUID requestKey=UUID.randomUUID();
        var useCase=new PublishProcessUseCase(store,publications,transactions,
                () -> new ActorContext(admin.id().toString(),true,Set.of("process.publish")),
                (action,type,id,diff)->{throw new IllegalStateException("audit failure after deployment");},validator,engine);
        var command=new PublishProcess(UUID.fromString(vid(saved)),1L);
        assertThatThrownBy(()->useCase.publish(UUID.fromString(id(saved)),command,requestKey)).isInstanceOf(IllegalStateException.class).hasMessage("audit failure after deployment");
        assertThat(jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select status from process_versions where id=?",String.class,command.versionId())).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select deployment_xml from process_versions where id=?",String.class,command.versionId())).isNull();
        assertThat(jdbc.queryForObject("select count(*) from process_versions where id=? and engine_deployment_id is null and engine_definition_id is null and engine_version is null and artifact_checksum is null and published_at is null and published_by is null",Long.class,command.versionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select active_process_version_id from process_definitions where id=?",UUID.class,UUID.fromString(id(saved)))).isNull();
        assertThat(jdbc.queryForObject("select count(*) from process_publications where request_key=?",Long.class,requestKey)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action='PROCESS_PUBLISHED' and entity_id=?",Long.class,id(saved))).isZero();
        mvc.perform(post(ROOT+"/"+id(saved)+"/publish").header("Authorization",admin.bearer()).header("Idempotency-Key",requestKey).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class)).isEqualTo(before+1);
    }

    @Test
    void concurrentReuseOfPublishKeyAcrossDefinitionsRollsBackLosingDeployment() throws Exception {
        var admin=actor("process.design","process.publish","form.design","form.publish");
        var a=validDraft(admin);var b=validDraft(admin);UUID requestKey=UUID.randomUUID();
        long before=jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class);
        var barrier=new CyclicBarrier(2);
        java.util.function.Function<JsonNode,Callable<Integer>> call=process->()->{
            barrier.await(30,java.util.concurrent.TimeUnit.SECONDS);
            return mvc.perform(post(ROOT+"/"+id(process)+"/publish").header("Authorization",admin.bearer()).header("Idempotency-Key",requestKey).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",vid(process),"expectedRevision",1)))).andReturn().getResponse().getStatus();
        };
        try(var pool=Executors.newFixedThreadPool(2)){var first=pool.submit(call.apply(a));var second=pool.submit(call.apply(b));assertThat(java.util.List.of(first.get(60,java.util.concurrent.TimeUnit.SECONDS),second.get(60,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}
        assertThat(jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class)).isEqualTo(before+1);
        assertThat(jdbc.queryForObject("select count(*) from process_publications where actor_id=? and request_key=?",Long.class,admin.id(),requestKey)).isEqualTo(1);
    }

    private JsonNode validDraft(TestActor admin) throws Exception {
        var process=create(admin,key(),"P4.7 publish");UUID form=publishedForm(admin);
        var step=Map.<String,Object>of("assignment",Map.of("strategy","REQUESTER_MANAGER"),"actions",java.util.List.of("APPROVE"));
        String template;try(var input=getClass().getResourceAsStream("/processes/p45-authoring.xml")){template=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        String xml=template.formatted(json.writeValueAsString(Map.of("step",step,"formVersionId",form))).replace("\"purchase\"","\""+process.get("definition").get("key").asString()+"\"");
        var model=new ProcessModelInput(xml,Map.of("review",step,"f2",Map.of("outcome","APPROVE")),Map.of("review",Map.of("formVersionId",form.toString())));
        return json.readTree(mvc.perform(put(ROOT+"/"+id(process)+"/draft").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SaveProcessDraft(UUID.fromString(vid(process)),0L,model)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    void validatesUnsavedModelWithRealReferencesAndSecurityWithoutDeployOrAudit() throws Exception {
        var admin=actor("process.design","form.design","form.publish");
        String processKey=key();
        var process=create(admin,processKey,"Validation");
        var form=publishedFixture(admin,process,55);
        var step=Map.<String,Object>of("assignment",Map.of("strategy","REQUESTER_MANAGER"),"actions",java.util.List.of("APPROVE"));
        String template;try(var input=getClass().getResourceAsStream("/processes/p45-authoring.xml")){template=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        String xml=template.formatted(json.writeValueAsString(Map.of("step",step,"formVersionId",form))).replace("\"purchase\"","\""+processKey+"\"");
        var model=new ProcessModelInput(xml,Map.of("review",step,"f2",Map.of("outcome","APPROVE")),Map.of("review",Map.of("formVersionId",form.toString())));
        String url=ROOT+"/"+id(process)+"/validate";
        var body=json.writeValueAsBytes(model);
        long deployments=jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class);

        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization",actor("process.start").bearer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        for(var tester:new TestActor[]{admin,actor("process.publish")}) mvc.perform(post(url).header("Authorization",tester.bearer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        jdbc.update("update form_versions set status = 'RETIRED' where id = ?",form);
        mvc.perform(post(url).header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.details[0].elementId").value("review"));
        assertThat(jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class)).isEqualTo(deployments);
        // Only actor fixtures above add identity audit; validation itself is read-only.
        assertThat(jdbc.queryForObject("select count(*) from audit_events where entity_id = ? and action <> 'PROCESS_CREATED'",Long.class,id(process))).isZero();
    }

    @Test
    void savesIncompleteDraftAndDetectsStaleRevisionAndPublishedMutations() throws Exception {
        var designer=actor("process.design","form.design","form.publish");var process=create(designer,key(),"Draft");
        String key=process.get("definition").get("key").asString();
        String xml="<b:definitions xmlns:b=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:d=\"http://www.omg.org/spec/BPMN/20100524/DI\" xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" id=\"defs\" targetNamespace=\"local\"><b:process id=\""+key+"\" isExecutable=\"true\"><b:startEvent id=\"start\"/></b:process><d:BPMNDiagram id=\"diagram\"><d:BPMNPlane id=\"plane\" bpmnElement=\""+key+"\"><d:BPMNShape id=\"start_di\" bpmnElement=\"start\"><dc:Bounds x=\"0\" y=\"0\" width=\"36\" height=\"36\"/></d:BPMNShape></d:BPMNPlane></d:BPMNDiagram></b:definitions>";
        var model=new ProcessModelInput(xml,Map.of(),Map.of());var command=new SaveProcessDraft(UUID.fromString(vid(process)),0L,model);String url=ROOT+"/"+id(process)+"/draft";
        var barrier=new CyclicBarrier(2);
        Callable<Integer> save=()->{barrier.await(30,java.util.concurrent.TimeUnit.SECONDS);return mvc.perform(put(url).header("Authorization",designer.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command))).andReturn().getResponse().getStatus();};
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(save);var b=pool.submit(save);assertThat(java.util.List.of(a.get(60,java.util.concurrent.TimeUnit.SECONDS),b.get(60,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}
        mvc.perform(put(url).header("Authorization",designer.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command))).andExpect(status().isConflict());
        mvc.perform(post(ROOT+"/"+id(process)+"/validate").header("Authorization",designer.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(model))).andExpect(status().isUnprocessableContent());
        mvc.perform(get(versionUrl(process)).header("Authorization",designer.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.data.version.bpmnXml").value(xml));
        mvc.perform(put(url).header("Authorization",actor("process.publish").bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SaveProcessDraft(UUID.fromString(vid(process)),1L,model)))).andExpect(status().isForbidden());
        publishedFixture(designer,process,99);
        mvc.perform(put(url).header("Authorization",designer.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SaveProcessDraft(UUID.fromString(vid(process)),1L,model)))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'PROCESS_DRAFT_SAVED' and entity_id = ?",Long.class,id(process))).isEqualTo(1);
    }

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
                (action, type, id, diff) -> { throw new IllegalStateException("audit unavailable"); }, org.mockito.Mockito.mock(ProcessValidationPort.class));
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
        UUID formVersionId=publishedForm(admin);
        jdbc.update("update process_versions set status = 'PUBLISHED', bpmn_xml = ?, step_config = ?::jsonb, form_bindings = ?::jsonb, form_version_id = ?, engine_deployment_id = ?, engine_definition_id = ?, engine_version = ?, artifact_checksum = ?, published_at = current_timestamp, published_by = ? where id = ?",
                XML, json.writeValueAsString(Map.of("review", Map.of("assignment", Map.of("strategy", "REQUESTER_MANAGER"), "actions", java.util.List.of("APPROVE", "REJECT")))),
                json.writeValueAsString(Map.of("review", Map.of("formVersionId", formVersionId))), formVersionId,
                "fixture-" + UUID.randomUUID(), "fixture-" + UUID.randomUUID(), engineVersion, "a".repeat(64), admin.id(), UUID.fromString(vid(process)));
        jdbc.update("update process_definitions set active_process_version_id = ? where id = ?", UUID.fromString(vid(process)), UUID.fromString(id(process)));
        return formVersionId;
    }

    @SuppressWarnings("unchecked")
    private UUID publishedForm(TestActor admin) throws Exception {
        Map<String, Object> body;
        try (var stream = getClass().getResourceAsStream("/forms/purchase-request.v1.json")) { body = json.readValue(stream, Map.class); }
        body.put("key", key());
        var form = json.readTree(mvc.perform(post("/api/v1/form-definitions").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        var formId = form.get("definition").get("id").asString();
        var formVersionId = UUID.fromString(form.get("version").get("id").asString());
        mvc.perform(post("/api/v1/form-definitions/" + formId + "/publish").header("Authorization", admin.bearer()).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId", formVersionId, "expectedRevision", 0)))).andExpect(status().isOk());
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
