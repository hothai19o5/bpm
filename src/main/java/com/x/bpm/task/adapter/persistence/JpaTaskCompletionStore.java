package com.x.bpm.task.adapter.persistence;

import com.x.bpm.businessrecord.adapter.persistence.BusinessRecordEntity;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.error.*;
import com.x.bpm.task.application.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.flowable.engine.HistoryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository @Transactional(propagation=Propagation.MANDATORY)
public class JpaTaskCompletionStore implements TaskCompletionStore {
    private final EntityManager entities;private final HistoryService history;private final TaskCompletionReceiptJpaRepository receipts;
    public JpaTaskCompletionStore(EntityManager entities,HistoryService history,TaskCompletionReceiptJpaRepository receipts){this.entities=entities;this.history=history;this.receipts=receipts;}
    public UUID locateRecord(String taskId){
        if(taskId==null||taskId.isBlank()||taskId.length()>255)throw new ApplicationException(ErrorCode.INVALID_REQUEST,"Mã công việc không hợp lệ.");
        var task=history.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        if(task!=null){var rows=entities.createQuery("select r.id from BusinessRecordEntity r where r.engineInstanceId=:instance",UUID.class).setParameter("instance",task.getProcessInstanceId()).getResultList();if(!rows.isEmpty())return rows.getFirst();}
        throw new ApplicationException(ErrorCode.NOT_FOUND,"Không tìm thấy công việc nghiệp vụ.");
    }
    public Optional<TaskCompletionResult> replay(UUID actorId,UUID key,String fingerprint){return receipts.findByActorIdAndRequestKey(actorId,key).map(receipt->{
        if(!receipt.getFingerprint().equals(fingerprint))throw reused();
        return new TaskCompletionResult(receipt.getRecord().getId(),receipt.getEngineTaskId(),receipt.getAction(),receipt.getRecordStatus(),receipt.getRecordRevision());});}
    public void prepare(BusinessTask task,CompleteTask command,UUID actorId,UUID key,String fingerprint,Map<String,Object> data){
        var record=entities.find(BusinessRecordEntity.class,task.recordId());
        record.setFormData(data);
        var log=new TaskActionLogEntity();log.setBusinessRecord(record);log.setEngineTaskId(task.engine().taskId());log.setActivityId(task.engine().taskDefinitionKey());
        log.setActor(entities.getReference(UserEntity.class,actorId));log.setAction(command.action().name());log.setComment(command.reason()==null?null:command.reason().strip());
        log.setIdempotencyKey(key.toString());log.setPayload(Map.of("fingerprint",fingerprint,"changedFields",java.util.stream.Stream.concat(command.patch().values().keySet().stream(),command.patch().removeFields().stream()).distinct().sorted().toList()));
        entities.persist(log);record.setLastTaskAction(log);entities.flush();
    }
    public TaskCompletionResult finish(BusinessTask task,String action,String status,Instant completedAt){
        var record=entities.find(BusinessRecordEntity.class,task.recordId());record.setStatus(BusinessRecordEntity.Status.valueOf(status));record.setCompletedAt(completedAt);entities.flush();
        return new TaskCompletionResult(record.getId(),task.engine().taskId(),action,record.getStatus().name(),record.getRevision());
    }
    public void remember(UUID actorId,UUID key,String fingerprint,TaskCompletionResult result){
        var receipt=new TaskCompletionReceiptEntity();receipt.setActor(entities.getReference(UserEntity.class,actorId));receipt.setRequestKey(key);receipt.setFingerprint(fingerprint);
        receipt.setRecord(entities.getReference(BusinessRecordEntity.class,result.recordId()));receipt.setEngineTaskId(result.completedTaskId());receipt.setAction(result.action());receipt.setRecordStatus(result.recordStatus());receipt.setRecordRevision(result.recordRevision());
        try{receipts.saveAndFlush(receipt);}catch(DataIntegrityViolationException conflict){throw reused();}
    }
    public boolean hasCorrectionTask(UUID recordId,Set<String> activeActivityIds){
        var steps=entities.find(BusinessRecordEntity.class,recordId).getProcessVersion().getStepConfig();
        return activeActivityIds.stream().anyMatch(id->steps.get(id) instanceof Map<?,?> step&&List.of("RESUBMIT").equals(step.get("actions")));
    }
    private static ApplicationException reused(){return new ApplicationException(ErrorCode.IDEMPOTENCY_KEY_REUSED,"Idempotency-Key đã dùng cho một thao tác công việc khác.");}
}
