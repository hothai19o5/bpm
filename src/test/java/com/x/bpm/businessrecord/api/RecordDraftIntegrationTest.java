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
    @Autowired RecordSubmissionStore submissions;
    @Autowired com.x.bpm.task.assignment.application.AssignmentDirectoryPort directory;
    @Autowired com.x.bpm.process.application.engine.ProcessEnginePort engine;
    @Autowired RecordEngineData engineData;
    @Autowired java.time.Clock clock;
    @Autowired org.flowable.engine.TaskService taskService;

    @Test void assignedInboxAndTaskDetailAreScopedAndHideTechnicalTasksAndHiddenFields() throws Exception {
        var approver=actor("task.complete");var process=publishedProcess(Map.of("strategy","SPECIFIC_USER","specificUserId",approver.id().toString()),false);
        var requester=actor(REQUESTER);department(requester);var record=postRecord(requester,createBody(process,completeData()),200);
        submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),200);var task=activeTask(record);
        var technical=taskService.newTask();technical.setName("Technical task");technical.setAssignee(approver.id().toString());taskService.saveTask(technical);
        try {
            var inbox=taskList(approver,"ASSIGNED",null,200);assertThat(inbox.get("totalElements").asLong()).isEqualTo(1);assertThat(inbox.get("items").get(0).get("id").asString()).isEqualTo(task.getId());
            assertThat(taskList(approver,"ASSIGNED","no such record",200).get("totalElements").asLong()).isZero();
            var detail=taskDetail(approver,task.getId(),200);assertThat(detail.get("canClaim").asBoolean()).isFalse();
            assertThat(detail.get("view").get("data").get("amount").asString()).isEqualTo(completeData().get("amount"));
            assertThat(detail.get("view").get("fieldAccess").get("budgetConfirmed").asString()).isEqualTo("READ_ONLY");
            assertThat(detail.get("view").get("schema").get("properties").has("budgetCode")).isFalse();
            taskDetail(requester,task.getId(),403);taskDetail(approver,technical.getId(),404);
            var auditor=actor("record.read.all","audit.read");taskList(auditor,"ASSIGNED",null,403);taskDetail(auditor,task.getId(),403);
            mvc.perform(get("/api/v1/tasks?size=101").header("Authorization",approver.bearer())).andExpect(status().isBadRequest());
        } finally {taskService.deleteTask(technical.getId(),true);}
    }

    @Test void candidateMembershipIsCurrentAndConcurrentClaimHasExactlyOneWinnerAndAudit() throws Exception {
        var a=actor("task.claim","task.complete");UUID scope=department(a);var b=actor("task.claim","task.complete");member(b,scope);
        var outsider=actor("task.claim","task.complete");department(outsider);
        var requester=actor("record.create","process.start","record.read.own","task.claim","task.complete");member(requester,scope);
        var process=publishedProcess(Map.of("strategy","DEPARTMENT","departmentId",scope.toString()),false);var record=postRecord(requester,createBody(process,completeData()),200);
        submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),200);String taskId=activeTask(record).getId();
        assertThat(taskList(a,"CANDIDATE",null,200).get("totalElements").asLong()).isEqualTo(1);
        assertThat(taskList(outsider,"CANDIDATE",null,200).get("totalElements").asLong()).isZero();
        assertThat(taskList(requester,"CANDIDATE",null,200).get("totalElements").asLong()).isZero();
        taskDetail(outsider,taskId,403);claim(outsider,taskId,403);claim(requester,taskId,403);
        // Membership is read now, not copied forever from task creation.
        var newcomer=actor("task.claim","task.complete");member(newcomer,scope);assertThat(taskList(newcomer,"CANDIDATE",null,200).get("totalElements").asLong()).isEqualTo(1);
        jdbc.update("delete from department_members where user_id=?",a.id());assertThat(taskList(a,"CANDIDATE",null,200).get("totalElements").asLong()).isZero();claim(a,taskId,403);member(a,scope);
        var barrier=new CyclicBarrier(2);List<Integer> statuses;
        try(var pool=Executors.newFixedThreadPool(2)){var first=pool.submit(()->{barrier.await(30,TimeUnit.SECONDS);return claimStatus(a,taskId);});var second=pool.submit(()->{barrier.await(30,TimeUnit.SECONDS);return claimStatus(b,taskId);});statuses=List.of(first.get(60,TimeUnit.SECONDS),second.get(60,TimeUnit.SECONDS));}
        assertThat(statuses).containsExactlyInAnyOrder(200,409);String assignee=activeTask(record).getAssignee();var winner=assignee.equals(a.id().toString())?a:b;var loser=winner==a?b:a;
        claim(winner,taskId,200);assertThat(taskList(winner,"ASSIGNED",null,200).get("totalElements").asLong()).isEqualTo(1);
        assertThat(taskList(loser,"CANDIDATE",null,200).get("totalElements").asLong()).isZero();taskDetail(loser,taskId,403);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action='TASK_CLAIMED' and entity_id=?",Long.class,taskId)).isEqualTo(1);
        assertThat(engine.findRunningInstancesByBusinessKey(record.get("businessKey").asString())).hasSize(1);
    }

    @Test void roleAndPermissionRevocationPreventClaimAndAuditFailureRollsClaimBack() throws Exception {
        var approver=actor("task.claim","task.complete");UUID scope=department(approver);UUID role=jdbc.queryForObject("select role_id from user_roles where user_id=?",UUID.class,approver.id());
        var process=publishedProcess(Map.of("strategy","ROLE","departmentId",scope.toString(),"roleId",role.toString()),false);
        var requester=actor(REQUESTER);department(requester);var record=postRecord(requester,createBody(process,completeData()),200);
        submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),200);String taskId=activeTask(record).getId();
        jdbc.update("update roles set status='INACTIVE' where id=?",role);claim(approver,taskId,403);jdbc.update("update roles set status='ACTIVE' where id=?",role);
        jdbc.execute("alter table audit_events add constraint p54_claim_fault check (action <> 'TASK_CLAIMED') not valid");
        try {claim(approver,taskId,500);}finally{jdbc.execute("alter table audit_events drop constraint p54_claim_fault");}
        assertThat(activeTask(record).getAssignee()).isNull();claim(approver,taskId,200);claim(approver,taskId,200);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action='TASK_CLAIMED' and entity_id=?",Long.class,taskId)).isEqualTo(1);
        jdbc.update("delete from role_permissions where role_id=? and permission_id=(select id from permissions where code='task.complete')",role);
        taskDetail(approver,taskId,403);claim(approver,taskId,403);
    }

    private JsonNode taskList(Actor actor,String box,String q,int expected)throws Exception{return json.readTree(mvc.perform(get("/api/v1/tasks").param("box",box).param("q",q==null?"":q).header("Authorization",actor.bearer())).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).get("data");}
    private JsonNode taskDetail(Actor actor,String taskId,int expected)throws Exception{return json.readTree(mvc.perform(get("/api/v1/tasks/"+taskId).header("Authorization",actor.bearer())).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).get("data");}
    private JsonNode claim(Actor actor,String taskId,int expected)throws Exception{return json.readTree(mvc.perform(post("/api/v1/tasks/"+taskId+"/claim").header("Authorization",actor.bearer())).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).get("data");}
    private int claimStatus(Actor actor,String taskId)throws Exception{return mvc.perform(post("/api/v1/tasks/"+taskId+"/claim").header("Authorization",actor.bearer())).andReturn().getResponse().getStatus();}

    @Test void runtimeAssignmentUsesPinnedDepartmentAndCurrentManagerOnlyForNewTasks() throws Exception {
        var process=publishedProcess(Map.of("strategy","REQUESTER_MANAGER"),true);
        var requester=actor(REQUESTER);UUID original=department(requester);
        var manager=actor("task.complete");member(manager,original);jdbc.update("update departments set manager_user_id=? where id=?",manager.id(),original);
        var record=postRecord(requester,createBody(process,completeData()),200);
        submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),200);
        var first=activeTask(record);assertThat(first.getAssignee()).isEqualTo(manager.id().toString());
        var replacement=actor("task.complete");member(replacement,original);jdbc.update("update departments set manager_user_id=? where id=?",replacement.id(),original);
        UUID moved=UUID.randomUUID();jdbc.update("insert into departments(id,created_at,updated_at,revision,code,name,status) values(?,current_timestamp,current_timestamp,0,?,'Moved runtime','ACTIVE')",moved,key());
        jdbc.update("update department_members set department_id=? where user_id=?",moved,requester.id());
        assertThat(activeTask(record).getAssignee()).isEqualTo(manager.id().toString());
        taskService.complete(first.getId(),Map.of("bpmOutcome","APPROVE"));
        var second=activeTask(record);assertThat(second.getTaskDefinitionKey()).isEqualTo("review2");assertThat(second.getAssignee()).isEqualTo(replacement.id().toString());
        assertThat(jdbc.queryForObject("select requester_department_id from business_records where id=?",UUID.class,id(record))).isEqualTo(original);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where entity_type='ENGINE_TASK' and entity_id in (?,?) and source='SYSTEM' and actor_id is null and action='TASK_ASSIGNED'",Long.class,first.getId(),second.getId())).isEqualTo(2);
    }

    @Test void runtimeSpecificDepartmentAndRoleAssignmentsStayInsideConfiguredScope() throws Exception {
        var approver=actor("task.claim","task.complete");UUID scope=department(approver);
        var outsider=actor("task.claim","task.complete");department(outsider);
        UUID role=jdbc.queryForObject("select role_id from user_roles where user_id=?",UUID.class,approver.id());
        for(var assignment:List.of(Map.of("strategy","SPECIFIC_USER","specificUserId",approver.id().toString()),
                Map.of("strategy","DEPARTMENT","departmentId",scope.toString()),
                Map.of("strategy","ROLE","departmentId",scope.toString(),"roleId",role.toString()))) {
            var process=publishedProcess(assignment,false);var requester=actor(REQUESTER);department(requester);
            var record=postRecord(requester,createBody(process,completeData()),200);
            submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),200);
            var task=activeTask(record);var links=taskService.getIdentityLinksForTask(task.getId());
            if(assignment.get("strategy").equals("SPECIFIC_USER")){assertThat(task.getAssignee()).isEqualTo(approver.id().toString());assertThat(links.stream().filter(l->"candidate".equals(l.getType()))).isEmpty();}
            else {assertThat(task.getAssignee()).isNull();assertThat(links.stream().filter(l->"candidate".equals(l.getType())).map(l->l.getGroupId()).toList())
                    .containsExactly("bpm:department:"+scope+(assignment.get("strategy").equals("ROLE")?":role:"+role:""));
                assertThat(links.stream().filter(l->"candidate".equals(l.getType())).map(l->l.getUserId()).toList()).containsOnlyNulls();}
            assertThat(jdbc.queryForObject("select count(*) from assignment_exceptions where record_id=?",Long.class,id(record))).isZero();
        }
    }

    @Test void assignmentFailuresRemainWaitingWithSystemAuditAndProtectedSupportMetadata() throws Exception {
        var process=publishedProcess();var requester=actor(REQUESTER);UUID scope=department(requester);
        var missing=postRecord(requester,createBody(process,completeData()),200);
        submit(requester,missing,new SubmitRecord(0L,missing.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),200);
        assertAssignmentFailure(missing,"MANAGER_MISSING");
        jdbc.update("update departments set manager_user_id=? where id=?",requester.id(),scope);
        // Self approval remains forbidden even when the requester otherwise has complete permission.
        UUID role=jdbc.queryForObject("select role_id from user_roles where user_id=?",UUID.class,requester.id());
        jdbc.update("insert into role_permissions(id,created_at,updated_at,revision,role_id,permission_id) values(?,current_timestamp,current_timestamp,0,?,?)",UUID.randomUUID(),role,jdbc.queryForObject("select id from permissions where code='task.complete'",UUID.class));
        var self=postRecord(requester,createBody(process,completeData()),200);
        submit(requester,self,new SubmitRecord(0L,self.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),200);assertAssignmentFailure(self,"SELF_APPROVAL");
        var support=actor("task.reassign");String payload=mvc.perform(get("/api/v1/tasks/assignment-exceptions?page=0&size=1").header("Authorization",support.bearer())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(payload).get("data").get("items").size()).isEqualTo(1);assertThat(json.readTree(payload).get("data").get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(payload).doesNotContain("formData","itemName","requesterId");
        mvc.perform(get("/api/v1/tasks/assignment-exceptions").header("Authorization",requester.bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tasks/assignment-exceptions?size=101").header("Authorization",support.bearer())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/tasks/assignment-exceptions")).andExpect(status().isUnauthorized());
    }

    @Test void runtimeInactiveUserAndEmptyOrInactiveQueuesDoNotBecomeCompanyWide() throws Exception {
        var approver=actor("task.claim","task.complete");UUID scope=department(approver);
        var cases=List.of(Map.of("strategy","SPECIFIC_USER","specificUserId",approver.id().toString()),Map.of("strategy","DEPARTMENT","departmentId",scope.toString()));
        for(var assignment:cases) {
            var process=publishedProcess(assignment,false);jdbc.update("update users set status='INACTIVE' where id=?",approver.id());
            var requester=actor(REQUESTER);department(requester);var record=postRecord(requester,createBody(process,completeData()),200);
            submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),200);
            assertAssignmentFailure(record,assignment.get("strategy").equals("SPECIFIC_USER")?"USER_INACTIVE":"EMPTY_CANDIDATE_GROUP");
            jdbc.update("update users set status='ACTIVE' where id=?",approver.id());
        }
        var process=publishedProcess(Map.of("strategy","DEPARTMENT","departmentId",scope.toString()),false);jdbc.update("update departments set status='INACTIVE' where id=?",scope);
        var requester=actor(REQUESTER);department(requester);var record=postRecord(requester,createBody(process,completeData()),200);
        submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),200);assertAssignmentFailure(record,"DEPARTMENT_INACTIVE");
    }

    @Test void assignmentJournalFaultRollsBackEngineRecordReceiptAndCanRetry() throws Exception {
        var process=publishedProcess();var requester=actor(REQUESTER);department(requester);var record=postRecord(requester,createBody(process,completeData()),200);
        var command=new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of()));UUID operation=UUID.randomUUID();
        jdbc.execute("alter table audit_events add constraint p53_assignment_fault check (action <> 'TASK_ASSIGNMENT_FAILED') not valid");
        try {submit(requester,record,command,operation,500);} finally {jdbc.execute("alter table audit_events drop constraint p53_assignment_fault");}
        assertThat(jdbc.queryForObject("select engine_instance_id from business_records where id=?",String.class,id(record))).isNull();
        assertThat(engine.findRunningInstancesByBusinessKey(record.get("businessKey").asString())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from assignment_exceptions where record_id=?",Long.class,id(record))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from record_submission_receipts where record_id=?",Long.class,id(record))).isZero();
        submit(requester,record,command,operation,200);assertAssignmentFailure(record,"MANAGER_MISSING");
    }

    private void member(Actor user,UUID scope){jdbc.update("insert into department_members(id,created_at,updated_at,revision,user_id,department_id,is_primary) values(?,current_timestamp,current_timestamp,0,?,?,true)",UUID.randomUUID(),user.id(),scope);}
    private org.flowable.task.api.Task activeTask(JsonNode record){return taskService.createTaskQuery().processInstanceId(jdbc.queryForObject("select engine_instance_id from business_records where id=?",String.class,id(record))).active().singleResult();}
    private void assertAssignmentFailure(JsonNode record,String reason){var task=activeTask(record);assertThat(task).isNotNull();assertThat(task.getAssignee()).isNull();assertThat(taskService.getIdentityLinksForTask(task.getId()).stream().filter(l->"candidate".equals(l.getType()))).isEmpty();
        assertThat(jdbc.queryForObject("select reason_code from assignment_exceptions where engine_task_id=?",String.class,task.getId())).isEqualTo(reason);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where entity_id=? and action='TASK_ASSIGNMENT_FAILED' and source='SYSTEM' and actor_id is null",Long.class,task.getId())).isEqualTo(1);}

    @Test void concurrentSubmitRetriesCreateOnePinnedInstanceAndReceiptAndFreezeDepartment() throws Exception {
        var process=publishedProcess();var requester=actor(REQUESTER);UUID department=department(requester);
        var record=postRecord(requester,createBody(process,completeData()),200);UUID key=UUID.randomUUID();
        var command=new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of()));
        var barrier=new CyclicBarrier(2);
        Callable<JsonNode> call=()->{barrier.await(30,TimeUnit.SECONDS);return submit(requester,record,command,key,200);};
        JsonNode submitted;
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(call);var b=pool.submit(call);submitted=a.get(60,TimeUnit.SECONDS);assertThat(b.get(60,TimeUnit.SECONDS)).isEqualTo(submitted);}
        assertThat(submitted.get("status").asString()).isEqualTo("IN_PROGRESS");assertThat(submitted.get("actions").isEmpty()).isTrue();
        String instance=jdbc.queryForObject("select engine_instance_id from business_records where id=?",String.class,id(record));
        assertThat(engine.findRunningInstance(instance).orElseThrow().definitionId()).isEqualTo(process.get("version").get("engineDefinitionId").asString());
        assertThat(engine.findRunningInstancesByBusinessKey(record.get("businessKey").asString())).hasSize(1);
        assertThat(jdbc.queryForObject("select requester_department_id from business_records where id=?",UUID.class,id(record))).isEqualTo(department);
        assertThat(jdbc.queryForObject("select count(*) from record_submission_receipts where record_id=?",Long.class,id(record))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action='RECORD_SUBMITTED' and entity_id=?",Long.class,id(record).toString())).isEqualTo(1);
        submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of("amount","44"))),key,409);
        submit(requester,record,command,UUID.randomUUID(),409);
        var publisher=actor("process.publish");mvc.perform(post("/api/v1/process-definitions/"+process.get("definition").get("id").asString()+"/retire").header("Authorization",publisher.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",versionId(process),"expectedRevision",2)))).andExpect(status().isOk());
        submit(requester,record,command,key,200);
        UUID moved=UUID.randomUUID();jdbc.update("insert into departments(id,created_at,updated_at,revision,code,name,status) values(?,current_timestamp,current_timestamp,0,?,'Moved','ACTIVE')",moved,key());
        jdbc.update("update department_members set department_id=? where user_id=?",moved,requester.id());
        assertThat(jdbc.queryForObject("select requester_department_id from business_records where id=?",UUID.class,id(record))).isEqualTo(department);
        mvc.perform(put(ROOT+"/"+id(record)+"/draft").header("Authorization",requester.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SaveRecordDraft(submitted.get("revision").asLong(),"No edit after submit",new FormDataPatch(Map.of()))))).andExpect(status().isConflict());
    }

    @Test void competingSubmitKeysAndReuseAcrossRecordsLeaveOnlyOneInstancePerOperation() throws Exception {
        var process=publishedProcess();var requester=actor(REQUESTER);department(requester);
        var record=postRecord(requester,createBody(process,completeData()),200);var command=new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of()));
        var barrier=new CyclicBarrier(2);Callable<Integer> call=()->{barrier.await(30,TimeUnit.SECONDS);return mvc.perform(post(ROOT+"/"+id(record)+"/submit").header("Authorization",requester.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command))).andReturn().getResponse().getStatus();};
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(call);var b=pool.submit(call);assertThat(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}
        assertThat(engine.findRunningInstancesByBusinessKey(record.get("businessKey").asString())).hasSize(1);
        var one=postRecord(requester,createBody(process,completeData()),200);var two=postRecord(requester,createBody(process,completeData()),200);
        UUID shared=UUID.randomUUID();var race=new CyclicBarrier(2);
        java.util.function.Function<JsonNode,Callable<Integer>> run=r->()->{race.await(30,TimeUnit.SECONDS);return mvc.perform(post(ROOT+"/"+id(r)+"/submit").header("Authorization",requester.bearer()).header("Idempotency-Key",shared).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SubmitRecord(0L,r.get("title").asString(),new FormDataPatch(Map.of()))))).andReturn().getResponse().getStatus();};
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(run.apply(one));var b=pool.submit(run.apply(two));assertThat(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}
        assertThat(engine.findRunningInstancesByBusinessKey(one.get("businessKey").asString()).size()+engine.findRunningInstancesByBusinessKey(two.get("businessKey").asString()).size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from record_submission_receipts where actor_id=? and request_key=?",Long.class,requester.id(),shared)).isEqualTo(1);
    }

    @Test void submitValidatesOwnerFieldsDepartmentAndCurrentVersionBeforeEngineAndAuditFailureRollsBack() throws Exception {
        var process=publishedProcess();var requester=actor(REQUESTER);var record=postRecord(requester,createBody(process,Map.of()),200);
        submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),422);
        submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(completeData())),UUID.randomUUID(),422);
        department(requester);
        var forbidden=new LinkedHashMap<>(completeData());forbidden.put("budgetConfirmed",true);
        submit(requester,record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(forbidden)),UUID.randomUUID(),403);
        submit(actor(REQUESTER),record,new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(completeData())),UUID.randomUUID(),403);
        var command=new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(completeData()));UUID key=UUID.randomUUID();
        var failing=new SubmitRecordUseCase(store,submissions,transactions,()->new ActorContext(requester.id().toString(),true,Set.of(REQUESTER)),forms,directory,engine,engineData,(action,type,id,diff)->{throw new IllegalStateException("submit audit unavailable");},clock);
        assertThatThrownBy(()->failing.submit(id(record),command,key)).isInstanceOf(IllegalStateException.class).hasMessage("submit audit unavailable");
        assertThat(engine.findRunningInstancesByBusinessKey(record.get("businessKey").asString())).isEmpty();
        assertThat(jdbc.queryForObject("select status from business_records where id=?",String.class,id(record))).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from business_records where id=? and engine_instance_id is null and submitted_at is null and requester_department_id is null and form_data='{}'::jsonb",Long.class,id(record))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from record_submission_receipts where request_key=?",Long.class,key)).isZero();
        submit(requester,record,command,key,200);
        var retiredDraft=postRecord(requester,createBody(process,completeData()),200);var publisher=actor("process.publish");
        mvc.perform(post("/api/v1/process-definitions/"+process.get("definition").get("id").asString()+"/retire").header("Authorization",publisher.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",versionId(process),"expectedRevision",2)))).andExpect(status().isOk());
        submit(requester,retiredDraft,new SubmitRecord(0L,retiredDraft.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),409);
        assertThat(engine.findRunningInstancesByBusinessKey(retiredDraft.get("businessKey").asString())).isEmpty();
    }

    @Test void submitUsesPinnedDefinitionIdAfterSelectingAnOlderBusinessVersion() throws Exception {
        var v1=publishedProcess();var requester=actor(REQUESTER);department(requester);var record=postRecord(requester,createBody(v1,completeData()),200);
        var admin=actor("process.design","process.publish");String definitionId=v1.get("definition").get("id").asString();
        var draft=json.readTree(mvc.perform(post("/api/v1/process-definitions/"+definitionId+"/clone").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",versionId(v1),"expectedRevision",2)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        var v2=json.readTree(mvc.perform(post("/api/v1/process-definitions/"+definitionId+"/publish").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",versionId(draft),"expectedRevision",0)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        var command=new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of()));
        submit(requester,record,command,UUID.randomUUID(),409);
        mvc.perform(post("/api/v1/process-definitions/"+definitionId+"/activate").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",versionId(v1),"expectedRevision",2)))).andExpect(status().isOk());
        submit(requester,record,command,UUID.randomUUID(),200);
        String instance=jdbc.queryForObject("select engine_instance_id from business_records where id=?",String.class,id(record));
        assertThat(engine.findRunningInstance(instance).orElseThrow().definitionId()).isEqualTo(v1.get("version").get("engineDefinitionId").asString()).isNotEqualTo(v2.get("version").get("engineDefinitionId").asString());
    }

    @Test void engineFailureAfterCreatingInstanceRollsBackPreparedDataAndAllowsSameKeyRetry() throws Exception {
        var process=publishedProcess();var requester=actor(REQUESTER);department(requester);var record=postRecord(requester,createBody(process,Map.of()),200);
        var faulty=org.mockito.Mockito.mock(com.x.bpm.process.application.engine.ProcessEnginePort.class,org.mockito.AdditionalAnswers.delegatesTo(engine));
        org.mockito.Mockito.doAnswer(invocation->{engine.start(invocation.getArgument(0),invocation.getArgument(1),invocation.getArgument(2));throw new IllegalStateException("engine fault after start");})
                .when(faulty).start(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap());
        var useCase=new SubmitRecordUseCase(store,submissions,transactions,()->new ActorContext(requester.id().toString(),true,Set.of(REQUESTER)),forms,directory,faulty,engineData,(action,type,id,diff)->{},clock);
        UUID key=UUID.randomUUID();var command=new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(completeData()));
        assertThatThrownBy(()->useCase.submit(id(record),command,key)).isInstanceOf(IllegalStateException.class).hasMessage("engine fault after start");
        assertThat(jdbc.queryForObject("select count(*) from flowable.act_hi_procinst where business_key_=?",Long.class,record.get("businessKey").asString())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from business_records where id=? and status='DRAFT' and engine_instance_id is null and submitted_at is null and requester_department_id is null and form_data='{}'::jsonb",Long.class,id(record))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from record_submission_receipts where request_key=?",Long.class,key)).isZero();submit(requester,record,command,key,200);
    }

    @Test @SuppressWarnings("unchecked") void scalarJsonVariablesDriveExactThresholdAndImmediateCompletion() throws Exception {
        var process=publishedProcess();var admin=actor("process.design","process.publish");String definitionId=process.get("definition").get("id").asString();
        var draft=json.readTree(mvc.perform(post("/api/v1/process-definitions/"+definitionId+"/clone").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",versionId(process),"expectedRevision",2)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        var condition=Map.of("field","amount","operator","GE","value","50000000");
        String extra="<b:exclusiveGateway id=\"route\" default=\"skip\"/><b:sequenceFlow id=\"high\" sourceRef=\"route\" targetRef=\"review\"><b:extensionElements><bpm:configuration>"+json.writeValueAsString(Map.of("step",Map.of("condition",condition)))+"</bpm:configuration></b:extensionElements></b:sequenceFlow><b:sequenceFlow id=\"skip\" sourceRef=\"route\" targetRef=\"end\"/>";
        String di="<d:BPMNShape id=\"route_di\" bpmnElement=\"route\" isMarkerVisible=\"true\"><dc:Bounds x=\"50\" y=\"100\" width=\"50\" height=\"50\"/></d:BPMNShape>";
        for(String flow:List.of("high","skip"))di+="<d:BPMNEdge id=\""+flow+"_di\" bpmnElement=\""+flow+"\"><di:waypoint x=\"100\" y=\"100\"/><di:waypoint x=\"250\" y=\"100\"/></d:BPMNEdge>";
        String xml=draft.get("version").get("bpmnXml").asString().replace("sourceRef=\"start\" targetRef=\"review\"","sourceRef=\"start\" targetRef=\"route\"").replace("</b:process>",extra+"</b:process>").replace("</d:BPMNPlane>",di+"</d:BPMNPlane>");
        var steps=new LinkedHashMap<String,Object>(json.convertValue(draft.get("version").get("stepConfig"),Map.class));steps.put("high",Map.of("condition",condition));steps.put("skip",Map.of("default",true));
        var model=new ProcessModelInput(xml,steps,json.convertValue(draft.get("version").get("formBindings"),Map.class));
        mvc.perform(put("/api/v1/process-definitions/"+definitionId+"/draft").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SaveProcessDraft(UUID.fromString(versionId(draft)),0L,model)))).andExpect(status().isOk());
        var configured=json.readTree(mvc.perform(post("/api/v1/process-definitions/"+definitionId+"/publish").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",versionId(draft),"expectedRevision",1)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        var requester=actor(REQUESTER);department(requester);
        for(String amount:List.of("49999999","50000000","50000001")) {
            var values=new LinkedHashMap<>(completeData());values.put("amount",amount);var record=postRecord(requester,createBody(configured,values),200);UUID key=UUID.randomUUID();
            var command=new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of()));var submitted=submit(requester,record,command,key,200);
            assertThat(submitted.get("status").asString()).isEqualTo(amount.equals("49999999")?"COMPLETED":"IN_PROGRESS");
            submit(requester,record,command,key,200);
            assertThat(jdbc.queryForObject("select count(*) from flowable.act_hi_procinst where business_key_=?",Long.class,record.get("businessKey").asString())).isEqualTo(1);
        }
    }

    private Map<String,Object> completeData(){return Map.of("itemName","Laptop","quantity",1,"amount","49999999","reason","Work");}
    @Test void submitPermissionIsIndependentOfDraftSaveAndRevocationAppliesImmediately() throws Exception {
        var process=publishedProcess();var requester=actor("record.create","process.start","record.read.own");department(requester);
        var record=postRecord(requester,createBody(process,completeData()),200);
        assertThat(record.get("actions").toString()).isEqualTo("[\"SUBMIT\"]");
        assertThat(record.get("view").get("fieldAccess").get("amount").asString()).isEqualTo("EDITABLE");
        mvc.perform(put(ROOT+"/"+id(record)+"/draft").header("Authorization",requester.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new SaveRecordDraft(0L,record.get("title").asString(),new FormDataPatch(Map.of()))))).andExpect(status().isForbidden());
        var command=new SubmitRecord(0L,record.get("title").asString(),new FormDataPatch(Map.of()));submit(requester,record,command,UUID.randomUUID(),200);
        var revoked=actor(REQUESTER);department(revoked);var draft=postRecord(revoked,createBody(process,completeData()),200);
        jdbc.update("delete from role_permissions where role_id in (select role_id from user_roles where user_id=?) and permission_id=(select id from permissions where code='process.start')",revoked.id());
        submit(revoked,draft,new SubmitRecord(0L,draft.get("title").asString(),new FormDataPatch(Map.of())),UUID.randomUUID(),403);
        assertThat(engine.findRunningInstancesByBusinessKey(draft.get("businessKey").asString())).isEmpty();
    }
    private UUID department(Actor actor){UUID department=UUID.randomUUID();jdbc.update("insert into departments(id,created_at,updated_at,revision,code,name,status) values(?,current_timestamp,current_timestamp,0,?,'Requester department','ACTIVE')",department,key());jdbc.update("insert into department_members(id,created_at,updated_at,revision,user_id,department_id,is_primary) values(?,current_timestamp,current_timestamp,0,?,?,true)",UUID.randomUUID(),actor.id(),department);return department;}
    private JsonNode submit(Actor actor,JsonNode record,SubmitRecord command,UUID key,int expected) throws Exception{return json.readTree(mvc.perform(post(ROOT+"/"+id(record)+"/submit").header("Authorization",actor.bearer()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).get("data");}

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
        var review=json.convertValue(clone.get("version").get("stepConfig").get("review"),Map.class);
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
    private JsonNode publishedProcess() throws Exception { return publishedProcess(Map.of("strategy","REQUESTER_MANAGER"),false); }
    @SuppressWarnings("unchecked")
    private JsonNode publishedProcess(Map<String,String> assignment,boolean sequential) throws Exception {
        var admin=actor("process.design","process.publish","form.design","form.publish");
        Map<String,Object> formBody;try(var input=getClass().getResourceAsStream("/forms/purchase-request.v1.json")){formBody=json.readValue(input,Map.class);}formBody.put("key",key());
        var form=json.readTree(mvc.perform(post("/api/v1/form-definitions").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(formBody))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        String formId=form.get("version").get("id").asString();
        mvc.perform(post("/api/v1/form-definitions/"+form.get("definition").get("id").asString()+"/publish").header("Authorization",admin.bearer()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("versionId",formId,"expectedRevision",0)))).andExpect(status().isOk());
        String processKey=key();var process=json.readTree(mvc.perform(post("/api/v1/process-definitions").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("key",processKey,"name","Record test")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        var entry=Map.<String,Object>of("fieldAccess",Map.of("itemName","EDITABLE","quantity","EDITABLE","amount","EDITABLE","reason","EDITABLE","proposalAttachments","EDITABLE","budgetCode","READ_ONLY"));
        var review=Map.<String,Object>of("assignment",assignment,"actions",List.of("APPROVE"),"fieldAccess",Map.of("itemName","READ_ONLY","amount","READ_ONLY","budgetConfirmed","EDITABLE"));
        String xml;try(var input=getClass().getResourceAsStream("/processes/p45-authoring.xml")){xml=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        xml=xml.formatted(json.writeValueAsString(Map.of("step",review,"formVersionId",formId))).replace("\"purchase\"","\""+processKey+"\"").replace("<b:startEvent id=\"start\"/>","<b:startEvent id=\"start\"><b:extensionElements><bpm:configuration>"+json.writeValueAsString(Map.of("step",entry,"formVersionId",formId))+"</bpm:configuration></b:extensionElements></b:startEvent>");
        var steps=new LinkedHashMap<String,Object>(Map.of("start",entry,"review",review,"f2",Map.of("outcome","APPROVE")));
        var bindings=new LinkedHashMap<String,Object>(Map.of("start",Map.of("formVersionId",formId),"review",Map.of("formVersionId",formId)));
        if(sequential){String metadata=json.writeValueAsString(Map.of("step",review,"formVersionId",formId));
            xml=xml.replace("<b:endEvent id=\"end\"/>","<b:userTask id=\"review2\"><b:extensionElements><bpm:configuration>"+metadata+"</bpm:configuration></b:extensionElements></b:userTask><b:endEvent id=\"end\"/>")
                    .replace("sourceRef=\"review\" targetRef=\"end\"","sourceRef=\"review\" targetRef=\"review2\"")
                    .replace("</b:process>","<b:sequenceFlow id=\"f3\" sourceRef=\"review2\" targetRef=\"end\"><b:extensionElements><bpm:configuration>{\"step\":{\"outcome\":\"APPROVE\"}}</bpm:configuration></b:extensionElements></b:sequenceFlow></b:process>")
                    .replace("<d:BPMNEdge id=\"e1\"","<d:BPMNShape id=\"s4\" bpmnElement=\"review2\"><dc:Bounds x=\"200\" y=\"100\" width=\"100\" height=\"80\"/></d:BPMNShape><d:BPMNEdge id=\"e3\" bpmnElement=\"f3\"><di:waypoint x=\"300\" y=\"140\"/><di:waypoint x=\"250\" y=\"18\"/></d:BPMNEdge><d:BPMNEdge id=\"e1\"");
            steps.put("review2",review);steps.put("f3",Map.of("outcome","APPROVE"));bindings.put("review2",Map.of("formVersionId",formId));}
        var model=new ProcessModelInput(xml,steps,bindings);
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
