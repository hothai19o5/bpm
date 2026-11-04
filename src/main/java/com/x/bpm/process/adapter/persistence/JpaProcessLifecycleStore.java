package com.x.bpm.process.adapter.persistence;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.process.application.*;
import com.x.bpm.shared.error.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository @Transactional
public class JpaProcessLifecycleStore implements ProcessLifecycleStore {
    private final EntityManager entities;
    private final ProcessVersionJpaRepository versions;
    private final ProcessLifecycleReceiptJpaRepository receipts;
    private final ProcessStore store;
    public JpaProcessLifecycleStore(EntityManager entities,ProcessVersionJpaRepository versions,ProcessLifecycleReceiptJpaRepository receipts,ProcessStore store) {
        this.entities=entities;this.versions=versions;this.receipts=receipts;this.store=store;
    }
    public Optional<ProcessArtifact> replay(UUID actor,UUID key,String fingerprint) {
        return receipts.findByActorIdAndRequestKey(actor,key).map(r->{
            if(!r.getFingerprint().equals(fingerprint))throw reused();
            return artifact(r.getTargetVersion());
        });
    }
    public void remember(UUID actor,UUID key,String fingerprint,ProcessArtifact target) {
        var receipt=new ProcessLifecycleReceiptEntity();receipt.setActor(entities.getReference(UserEntity.class,actor));receipt.setRequestKey(key);
        receipt.setFingerprint(fingerprint);receipt.setTargetVersion(entities.getReference(ProcessVersionEntity.class,target.version().metadata().id()));
        try{receipts.saveAndFlush(receipt);}catch(DataIntegrityViolationException ex){throw reused();}
    }
    public ProcessArtifact cloneVersion(UUID id,UUID sourceId,UUID actor) {
        var source=version(id,sourceId);var copy=new ProcessVersionEntity();copy.setProcessDefinition(source.getProcessDefinition());
        int maximum=entities.createQuery("select max(v.versionNumber) from ProcessVersionEntity v where v.processDefinition.id=:id",Integer.class).setParameter("id",id).getSingleResult();
        if(maximum==Integer.MAX_VALUE)throw new ApplicationException(ErrorCode.REVISION_CONFLICT,"Đã hết số phiên bản.");
        copy.setVersionNumber(maximum+1);copy.setCreatedBy(entities.getReference(UserEntity.class,actor));
        copy.setBpmnXml(source.getBpmnXml());copy.setStepConfig(new LinkedHashMap<>(source.getStepConfig()));copy.setFormBindings(new LinkedHashMap<>(source.getFormBindings()));copy.setFormVersion(source.getFormVersion());
        return artifact(versions.saveAndFlush(copy));
    }
    public ProcessArtifact retire(UUID id,UUID versionId) {
        var version=version(id,versionId);version.setStatus(ProcessVersionEntity.Status.RETIRED);
        var definition=version.getProcessDefinition();
        if(definition.getActiveProcessVersion()!=null&&definition.getActiveProcessVersion().getId().equals(versionId))definition.setActiveProcessVersion(null);
        versions.saveAndFlush(version);return artifact(version);
    }
    public ProcessArtifact activate(UUID id,UUID versionId) {
        var version=version(id,versionId);
        if(version.getProcessDefinition().getStatus()!=ProcessDefinitionEntity.Status.ACTIVE||version.getEngineDefinitionId()==null||!version.isRecordStartReady())
            throw new ApplicationException(ErrorCode.VERSION_NOT_STARTABLE,"Phiên bản không thể dùng cho hồ sơ mới.");
        version.getProcessDefinition().setActiveProcessVersion(version);entities.flush();return artifact(version);
    }
    private ProcessVersionEntity version(UUID id,UUID versionId){return versions.findByIdAndProcessDefinitionId(versionId,id).orElseThrow();}
    private ProcessArtifact artifact(ProcessVersionEntity v){return store.version(v.getProcessDefinition().getId(),v.getId()).orElseThrow();}
    private static ApplicationException reused(){return new ApplicationException(ErrorCode.IDEMPOTENCY_KEY_REUSED,"Idempotency-Key đã dùng cho request vòng đời khác.");}
}
