package com.x.bpm.businessrecord.api;

import com.x.bpm.businessrecord.application.*;
import com.x.bpm.form.application.*;
import com.x.bpm.identity.application.*;
import com.x.bpm.identity.domain.*;
import com.x.bpm.process.application.*;
import com.x.bpm.process.adapter.persistence.ProcessVersionEntity;
import com.x.bpm.support.IsolatedPostgresTest;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IsolatedPostgresTest @AutoConfigureMockMvc
class RecordDraftIntegrationTest {
    static final String ROOT="/api/v1/records";
    static final String[] REQUESTER={"record.create","process.start","record.read.own","record.update.draft","record.delete.draft"};
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired AuthAccountPort accounts; @Autowired PasswordHashPort passwords;
    @Autowired RecordStore store; @Autowired RecordTransactionRunner transactions; @Autowired FormDataService forms;
    @Autowired EntityManager entities;

    @Test void createsFilteredIncompleteDraftWithServerIdentityAndNoEngineThenRejectsTampering() throws Exception {
        var process=publishedProcess();var requester=actor(REQUESTER);
        long instances=jdbc.queryForObject("select count(*) from flowable.act_hi_procinst",Long.class);
        var body=new LinkedHashMap<String,Object>(createBody(process,Map.of("amount","9007199254740993")));
        body.put("requesterId",UUID.randomUUID());body.put("status","COMPLETED");body.put("businessKey","forged");body.put("engineInstanceId","forged");
        var record=postRecord(requester,body,200);
        assertThat(record.get("requesterId").asString()).isEqualTo(requester.id().toString());
        assertThat(record.get("status").asString()).isEqualTo("DRAFT");assertThat(record.get("businessKey").asString()).startsWith("BPM-").isNotEqualTo("forged");
        assertThat(record.get("view").get("data").get("amount").asString()).isEqualTo("9007199254740993");
        assertThat(record.toString()).doesNotContain("budgetConfirmed","engineInstanceId");
        assertThat(jdbc.queryForObject("select engine_instance_id from business_records where id=?",String.class,id(record))).isNull();
        assertThat(jdbc.queryForObject("select count(*) from flowable.act_hi_procinst",Long.class)).isEqualTo(instances);
        mvc.perform(get(ROOT+"/"+id(record)).header("Authorization",requester.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.data.view.data.amount").value("9007199254740993"));
        for(var field:new String[]{"budgetConfirmed","budgetCode","unknown"})postRecord(requester,createBody(process,Map.of(field,"forged")),403);
        postRecord(requester,createBody(process,Map.of("amount",100)),422);
        postRecord(requester,createBody(process,Map.of("proposalAttachments",List.of(UUID.randomUUID().toString()))),422);
        assertThat(jdbc.queryForObject("select count(*) from business_records where requester_id=?",Long.class,requester.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action='RECORD_DRAFT_CREATED' and entity_id=?",Long.class,id(record).toString())).isEqualTo(1);
    }

    @Test void concurrentSavesDetectStaleRevisionAndPinnedDraftSurvivesProcessRetirement() throws Exception {
        var process=publishedProcess();var requester=actor(REQUESTER);var record=postRecord(requester,createBody(process,Map.of()),200);
        var command=new SaveRecordDraft(0L,"Saved title",new FormDataPatch(Map.of("amount","42")));
        var barrier=new CyclicBarrier(2);
        Callable<Integer> save=()->{barrier.await(30,TimeUnit.SECONDS);return mvc.perform(put(ROOT+"/"+id(record)+"/draft").header("Authorization",requester.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command))).andReturn().getResponse().getStatus();};
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(save);var b=pool.submit(save);assertThat(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}
        var publisher=actor("process.publish");
        mvc.perform(post("/api/v1/process-definitions/"+process.get("definition").get("id").asString()+"/retire").header("Authorization",publisher.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("versionId",versionId(process),"expectedRevision",2)))).andExpect(status().isOk());
        postRecord(requester,createBody(process,Map.of()),409);
        var saved=json.readTree(mvc.perform(put(ROOT+"/"+id(record)+"/draft").header("Authorization",requester.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(new SaveRecordDraft(1L,"Pinned draft",new FormDataPatch(Map.of("amount","43")))))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        assertThat(saved.get("processVersionId").asString()).isEqualTo(versionId(process));assertThat(saved.get("formVersionId")).isEqualTo(record.get("formVersionId"));
        assertThat(saved.get("view").get("data").get("amount").asString()).isEqualTo("43");
        mvc.perform(delete(ROOT+"/"+id(record)+"/draft").header("Authorization",requester.bearer()).param("expectedRevision","1")).andExpect(status().isConflict());
        mvc.perform(delete(ROOT+"/"+id(record)+"/draft").header("Authorization",requester.bearer()).param("expectedRevision","2")).andExpect(status().isOk());
        mvc.perform(get(ROOT+"/"+id(record)).header("Authorization",requester.bearer())).andExpect(status().isNotFound());
    }

    @Test void mineScopesCountAndPaginationAndOutsidersCannotReadUpdateOrDelete() throws Exception {
        var process=publishedProcess();var a=actor(REQUESTER);var b=actor(REQUESTER);
        var first=postRecord(a,createBody(process,Map.of()),200);postRecord(a,createBody(process,Map.of()),200);var other=postRecord(b,createBody(process,Map.of()),200);
        mvc.perform(get(ROOT+"/mine").header("Authorization",a.bearer()).param("size","1").param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get(ROOT+"/mine").header("Authorization",a.bearer()).param("q","%")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get(ROOT+"/mine").header("Authorization",a.bearer()).param("status","IN_PROGRESS")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get(ROOT+"/"+id(other)).header("Authorization",a.bearer())).andExpect(status().isForbidden());
        mvc.perform(put(ROOT+"/"+id(other)+"/draft").header("Authorization",a.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SaveRecordDraft(0L,"Forged",new FormDataPatch(Map.of()))))).andExpect(status().isForbidden());
        mvc.perform(delete(ROOT+"/"+id(other)+"/draft").header("Authorization",a.bearer()).param("expectedRevision","0")).andExpect(status().isForbidden());
        var admin=actor("record.read.all","record.update.draft","record.delete.draft");
        mvc.perform(get(ROOT+"/"+id(first)).header("Authorization",admin.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.data.actions").isEmpty()).andExpect(jsonPath("$.data.view.fieldAccess.amount").value("READ_ONLY"));
        mvc.perform(put(ROOT+"/"+id(first)+"/draft").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SaveRecordDraft(0L,"Admin cannot edit",new FormDataPatch(Map.of()))))).andExpect(status().isForbidden());
        mvc.perform(get(ROOT+"/"+id(first))).andExpect(status().isUnauthorized());
        postRecord(actor("record.create"),createBody(process,Map.of()),403);postRecord(actor("process.start"),createBody(process,Map.of()),403);
        for(var pair:new String[][]{{"q","x".repeat(201)},{"status","BAD"},{"sort","requesterId,asc"},{"size","101"}})mvc.perform(get(ROOT+"/mine").header("Authorization",a.bearer()).param(pair[0],pair[1])).andExpect(status().isBadRequest());
        jdbc.update("delete from user_roles where user_id=?",a.id());mvc.perform(get(ROOT+"/"+id(first)).header("Authorization",a.bearer())).andExpect(status().isForbidden());
    }

    @Test void auditFailuresRollBackCreateSaveDeleteAndPublishedArtifactsCannotBeChanged() throws Exception {
        var process=publishedProcess();var requester=actor(REQUESTER);
        var failing=new RecordDraftUseCase(store,transactions,()->new ActorContext(requester.id().toString(),true,Set.of(REQUESTER)),forms,(action,type,id,diff)->{throw new IllegalStateException("record audit unavailable");});
        var command=new CreateRecordDraft(UUID.fromString(process.get("definition").get("id").asString()),UUID.fromString(versionId(process)),"Rollback",new FormDataPatch(Map.of("amount","1")));
        assertThatThrownBy(()->failing.create(command)).isInstanceOf(IllegalStateException.class).hasMessage("record audit unavailable");
        assertThat(jdbc.queryForObject("select count(*) from business_records where requester_id=?",Long.class,requester.id())).isZero();
        var record=postRecord(requester,createBody(process,Map.of("amount","2")),200);
        assertThatThrownBy(()->failing.save(id(record),new SaveRecordDraft(0L,"Rollback save",new FormDataPatch(Map.of("amount","3"))))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->failing.delete(id(record),0L)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select form_data->>'amount' from business_records where id=?",String.class,id(record))).isEqualTo("2");
        assertThat(jdbc.queryForObject("select revision from business_records where id=?",Long.class,id(record))).isZero();
        assertThatThrownBy(()->transactions.required(()->{var version=entities.find(ProcessVersionEntity.class,UUID.fromString(versionId(process)));version.setBpmnXml("mutated");entities.flush();return null;})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select bpmn_xml from process_versions where id=?",String.class,UUID.fromString(versionId(process)))).contains("startEvent");
    }

    @Test void publishingWithoutRequesterFieldsIsRejectedBeforeDeploymentAndCannotReplaceActiveVersion() throws Exception {
        var process=publishedProcess();var admin=actor("process.design","process.publish");String definitionId=process.get("definition").get("id").asString();
        var clone=json.readTree(mvc.perform(post("/api/v1/process-definitions/"+definitionId+"/clone").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",versionId(process),"expectedRevision",2)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        String xml=clone.get("version").get("bpmnXml").asString().replaceFirst("<b:startEvent id=\"start\">.*?</b:startEvent>","<b:startEvent id=\"start\"/>");
        var review=Map.<String,Object>of("assignment",Map.of("strategy","REQUESTER_MANAGER"),"actions",List.of("APPROVE"));
        var model=new ProcessModelInput(xml,Map.of("review",review,"f2",Map.of("outcome","APPROVE")),Map.of("review",Map.of("formVersionId",process.get("version").get("formVersionId").asString())));
        mvc.perform(put("/api/v1/process-definitions/"+definitionId+"/draft").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SaveProcessDraft(UUID.fromString(versionId(clone)),0L,model)))).andExpect(status().isOk());
        long before=jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class);
        mvc.perform(post("/api/v1/process-definitions/"+definitionId+"/publish").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",versionId(clone),"expectedRevision",1)))).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.details[0].elementId").value("start"));
        assertThat(jdbc.queryForObject("select count(*) from flowable.act_re_deployment",Long.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select active_process_version_id from process_definitions where id=?",UUID.class,UUID.fromString(definitionId))).isEqualTo(UUID.fromString(versionId(process)));
    }

    private Map<String,Object> createBody(JsonNode process,Map<String,Object> values){return Map.of("processDefinitionId",process.get("definition").get("id").asString(),"processVersionId",versionId(process),"title","Record "+key(),"patch",new FormDataPatch(values));}
    private JsonNode postRecord(Actor actor,Map<String,Object> body,int expected) throws Exception{return json.readTree(mvc.perform(post(ROOT).header("Authorization",actor.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).get("data");}
    private UUID id(JsonNode record){return UUID.fromString(record.get("id").asString());}
    private String versionId(JsonNode process){return process.get("version").get("metadata").get("id").asString();}
    private String key(){return "p51-"+UUID.randomUUID();}
    @SuppressWarnings("unchecked")
    private JsonNode publishedProcess() throws Exception {
        var admin=actor("process.design","process.publish","form.design","form.publish");
        Map<String,Object> formBody;try(var input=getClass().getResourceAsStream("/forms/purchase-request.v1.json")){formBody=json.readValue(input,Map.class);}formBody.put("key",key());
        var form=json.readTree(mvc.perform(post("/api/v1/form-definitions").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(formBody))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        String formId=form.get("version").get("id").asString();
        mvc.perform(post("/api/v1/form-definitions/"+form.get("definition").get("id").asString()+"/publish").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",formId,"expectedRevision",0)))).andExpect(status().isOk());
        String processKey=key();var process=json.readTree(mvc.perform(post("/api/v1/process-definitions").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("key",processKey,"name","Record test")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        var entry=Map.<String,Object>of("fieldAccess",Map.of("itemName","EDITABLE","quantity","EDITABLE","amount","EDITABLE","reason","EDITABLE","proposalAttachments","EDITABLE","budgetCode","READ_ONLY"));
        var review=Map.<String,Object>of("assignment",Map.of("strategy","REQUESTER_MANAGER"),"actions",List.of("APPROVE"));
        String xml;try(var input=getClass().getResourceAsStream("/processes/p45-authoring.xml")){xml=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        xml=xml.formatted(json.writeValueAsString(Map.of("step",review,"formVersionId",formId))).replace("\"purchase\"","\""+processKey+"\"").replace("<b:startEvent id=\"start\"/>","<b:startEvent id=\"start\"><b:extensionElements><bpm:configuration>"+json.writeValueAsString(Map.of("step",entry,"formVersionId",formId))+"</bpm:configuration></b:extensionElements></b:startEvent>");
        var model=new ProcessModelInput(xml,Map.of("start",entry,"review",review,"f2",Map.of("outcome","APPROVE")),Map.of("start",Map.of("formVersionId",formId),"review",Map.of("formVersionId",formId)));
        String processId=process.get("definition").get("id").asString();
        mvc.perform(put("/api/v1/process-definitions/"+processId+"/draft").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SaveProcessDraft(UUID.fromString(versionId(process)),0L,model)))).andExpect(status().isOk());
        return json.readTree(mvc.perform(post("/api/v1/process-definitions/"+processId+"/publish").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",versionId(process),"expectedRevision",1)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
    private record Actor(UUID id,String token){String bearer(){return "Bearer "+token;}}
    private Actor actor(String... permissions) throws Exception {
        String password="record-test-only-2026";var user=accounts.create(new NewUserAccount(key(),"Record tester",passwords.hash(password),AccountStatus.ACTIVE,false));UUID role=UUID.randomUUID();
        jdbc.update("insert into roles(id,created_at,updated_at,revision,code,name,status) values(?,current_timestamp,current_timestamp,0,?,'Record test','ACTIVE')",role,key());
        jdbc.update("insert into user_roles(id,created_at,updated_at,revision,user_id,role_id) values(?,current_timestamp,current_timestamp,0,?,?)",UUID.randomUUID(),user.id(),role);
        for(String permission:permissions)jdbc.update("insert into role_permissions(id,created_at,updated_at,revision,role_id,permission_id) values(?,current_timestamp,current_timestamp,0,?,?)",UUID.randomUUID(),role,jdbc.queryForObject("select id from permissions where code=?",UUID.class,permission));
        String token=json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",user.username(),"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data").get("accessToken").asString();return new Actor(user.id(),token);
    }
}
