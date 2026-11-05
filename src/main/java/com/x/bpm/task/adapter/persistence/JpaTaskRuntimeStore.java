package com.x.bpm.task.adapter.persistence;

import com.x.bpm.businessrecord.adapter.persistence.BusinessRecordEntity;
import com.x.bpm.form.domain.*;
import com.x.bpm.shared.error.*;
import com.x.bpm.task.application.*;
import com.x.bpm.task.application.engine.EngineTask;
import com.x.bpm.task.assignment.application.RuntimeAssignmentStore;
import jakarta.persistence.*;
import java.util.*;
import java.util.stream.Collectors;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component @Transactional(propagation=Propagation.MANDATORY)
public class JpaTaskRuntimeStore implements TaskRuntimeStore {
    private final EntityManager entities;
    private final TaskService tasks;
    private final RuntimeAssignmentStore assignments;
    public JpaTaskRuntimeStore(EntityManager entities,TaskService tasks,RuntimeAssignmentStore assignments){this.entities=entities;this.tasks=tasks;this.assignments=assignments;}

    public List<BusinessTask> assigned(String userId){return tasks.createTaskQuery().active().taskAssignee(userId).includeIdentityLinks().list().stream().map(this::map).flatMap(Optional::stream).toList();}
    public List<BusinessTask> candidates(Set<String> groups){if(groups.isEmpty())return List.of();return tasks.createTaskQuery().active().taskUnassigned().taskCandidateGroupIn(new ArrayList<>(groups)).includeIdentityLinks().list().stream().map(this::map).flatMap(Optional::stream).toList();}
    public BusinessTask find(String taskId){if(taskId==null||taskId.isBlank()||taskId.length()>255)throw new ApplicationException(ErrorCode.INVALID_REQUEST,"Mã công việc không hợp lệ.");
        var task=tasks.createTaskQuery().active().taskId(taskId).includeIdentityLinks().singleResult();return task==null?missing():map(task).orElseThrow(JpaTaskRuntimeStore::notFound);}
    private BusinessTask missing(){throw notFound();}
    private static ApplicationException notFound(){return new ApplicationException(ErrorCode.NOT_FOUND,"Không tìm thấy công việc đang hoạt động.");}
    public void lockRecord(UUID id){var record=entities.find(BusinessRecordEntity.class,id,LockModeType.PESSIMISTIC_WRITE);if(record!=null)entities.refresh(record);}

    private Optional<BusinessTask> map(Task task){
        var records=entities.createQuery("select r from BusinessRecordEntity r where r.engineInstanceId=:instance and r.status in ('IN_PROGRESS','CHANGES_REQUESTED')",BusinessRecordEntity.class).setParameter("instance",task.getProcessInstanceId()).getResultList();
        if(records.isEmpty())return Optional.empty();var record=records.getFirst();
        var context=assignments.context(record.getId(),task.getProcessDefinitionId(),task.getProcessInstanceId(),task.getTaskDefinitionKey());
        var users=task.getIdentityLinks().stream().filter(l->"candidate".equals(l.getType())&&l.getUserId()!=null).map(l->l.getUserId()).collect(Collectors.toSet());
        var groups=task.getIdentityLinks().stream().filter(l->"candidate".equals(l.getType())&&l.getGroupId()!=null).map(l->l.getGroupId()).collect(Collectors.toSet());
        var engine=new EngineTask(task.getId(),task.getProcessInstanceId(),task.getProcessDefinitionId(),task.getTaskDefinitionKey(),task.getName(),task.getAssignee(),users,groups,task.getCreateTime()==null?null:task.getCreateTime().toInstant());
        return Optional.of(new BusinessTask(engine,record.getId(),record.getBusinessKey(),record.getTitle(),record.getStatus().name(),record.getRevision(),record.getRequester().getId(),context.request(),context.requesterCorrection()));
    }

    public TaskFormContext form(BusinessTask task){
        var record=entities.find(BusinessRecordEntity.class,task.recordId());var version=record.getFormVersion();
        var binding=record.getProcessVersion().getFormBindings().get(task.engine().taskDefinitionKey());
        if(!(binding instanceof Map<?,?> map)||!version.getId().toString().equals(map.get("formVersionId")))throw new IllegalStateException("Task form does not match pinned record schema");
        var raw=record.getProcessVersion().getStepConfig().get(task.engine().taskDefinitionKey());
        Map<?,?> step=raw instanceof Map<?,?> value?value:Map.of();Map<?,?> fields=step.get("fieldAccess") instanceof Map<?,?> access?access:Map.of();
        var policy=new LinkedHashMap<String,FormFieldAccess>();fields.forEach((id,access)->policy.put((String)id,FormFieldAccess.valueOf((String)access)));
        var form=new FormVersion(version.getId(),version.getFormDefinition().getId(),version.getVersionNumber(),FormVersion.Status.valueOf(version.getStatus().name()),version.getSchemaJson(),version.getUiSchemaJson(),version.getCreatedBy().getId(),version.getPublishedBy()==null?null:version.getPublishedBy().getId(),version.getPublishedAt(),version.getRevision());
        var actions=step.get("actions") instanceof List<?> values?values.stream().map(value->FormAction.valueOf((String)value)).toList():List.<FormAction>of();
        return new TaskFormContext(form,new FormFieldPolicy(policy),record.getFormData(),actions);
    }
}
