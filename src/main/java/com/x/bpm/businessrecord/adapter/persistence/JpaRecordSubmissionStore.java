package com.x.bpm.businessrecord.adapter.persistence;
import com.x.bpm.businessrecord.application.*;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.organization.adapter.persistence.DepartmentEntity;
import com.x.bpm.shared.error.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository @Transactional
public class JpaRecordSubmissionStore implements RecordSubmissionStore {
    private final EntityManager entities;
    private final RecordSubmissionReceiptJpaRepository receipts;
    private final RecordStore records;
    public JpaRecordSubmissionStore(EntityManager entities,RecordSubmissionReceiptJpaRepository receipts,RecordStore records){this.entities=entities;this.receipts=receipts;this.records=records;}
    public boolean replay(UUID actorId,UUID key,String fingerprint,UUID recordId) {
        var previous=receipts.findByActorIdAndRequestKey(actorId,key);if(previous.isEmpty())return false;
        var receipt=previous.get();
        if(!receipt.getFingerprint().equals(fingerprint)||!receipt.getRecord().getId().equals(recordId))throw reused();
        if(!receipt.getEngineInstanceId().equals(receipt.getRecord().getEngineInstanceId()))throw new ApplicationException(ErrorCode.INTERNAL_ERROR,"Mapping biên nhận không hợp lệ.");
        return true;
    }
    public void prepare(UUID id,UUID departmentId,String title,Map<String,Object> data) {
        var record=entities.find(BusinessRecordEntity.class,id);record.setTitle(title);record.setFormData(data);record.setRequesterDepartment(entities.getReference(DepartmentEntity.class,departmentId));
        // Keep the managed snapshot visible to synchronous engine hooks. Flush once with the final state.
    }
    public BusinessRecord finish(UUID id,String instanceId,Instant submittedAt,Instant completedAt) {
        var record=entities.find(BusinessRecordEntity.class,id);record.setEngineInstanceId(instanceId);record.setSubmittedAt(submittedAt);record.setCompletedAt(completedAt);
        record.setStatus(completedAt==null?BusinessRecordEntity.Status.IN_PROGRESS:BusinessRecordEntity.Status.COMPLETED);
        entities.flush();return records.find(id,false).orElseThrow();
    }
    public void remember(UUID actorId,UUID key,String fingerprint,UUID recordId,String instanceId) {
        var receipt=new RecordSubmissionReceiptEntity();receipt.setActor(entities.getReference(UserEntity.class,actorId));receipt.setRequestKey(key);receipt.setFingerprint(fingerprint);
        receipt.setRecord(entities.getReference(BusinessRecordEntity.class,recordId));receipt.setEngineInstanceId(instanceId);
        try{receipts.saveAndFlush(receipt);}catch(DataIntegrityViolationException ex){throw reused();}
    }
    private static ApplicationException reused(){return new ApplicationException(ErrorCode.IDEMPOTENCY_KEY_REUSED,"Idempotency-Key đã dùng cho nội dung gửi hồ sơ khác.");}
}
