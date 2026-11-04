package com.x.bpm.process.adapter.persistence;

import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.process.application.*;
import com.x.bpm.process.application.engine.EngineProcessDefinition;
import com.x.bpm.shared.error.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaProcessPublicationStore implements ProcessPublicationStore {
    private final ProcessDefinitionJpaRepository definitions;
    private final ProcessVersionJpaRepository versions;
    private final ProcessStore store;
    private final EntityManager entities;
    private final Clock clock;
    private final ProcessPublicationJpaRepository receipts;
    public JpaProcessPublicationStore(ProcessDefinitionJpaRepository definitions,ProcessVersionJpaRepository versions,
            ProcessStore store,EntityManager entities,Clock clock,ProcessPublicationJpaRepository receipts) {
        this.definitions=definitions;this.versions=versions;this.store=store;this.entities=entities;this.clock=clock;
        this.receipts=receipts;
    }
    public void lock(UUID id) {
        definitions.lockById(id).orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,"Không tìm thấy quy trình."));
    }
    public Optional<ProcessArtifact> replay(UUID id,PublishProcess command,UUID actorId,UUID key) {
        var receipts=entities.createQuery("select r from ProcessPublicationEntity r where r.actor.id=:actor and r.requestKey=:key",ProcessPublicationEntity.class)
                .setParameter("actor",actorId).setParameter("key",key).getResultList();
        if(receipts.isEmpty())return Optional.empty();
        var receipt=receipts.getFirst();
        if(!receipt.getProcessDefinition().getId().equals(id) || !receipt.getProcessVersion().getId().equals(command.versionId())
                || receipt.getSourceRevision()!=command.expectedRevision())throw reused();
        return store.version(id,command.versionId());
    }
    public ProcessArtifact freeze(UUID id,PublishProcess command,UUID actorId,UUID key,String xml,String checksum,EngineProcessDefinition engine) {
        var version=versions.findByIdAndProcessDefinitionId(command.versionId(),id).orElseThrow();
        var actor=entities.getReference(UserEntity.class,actorId);
        version.setDeploymentXml(xml);version.setArtifactChecksum(checksum);
        version.setEngineDeploymentId(engine.deploymentId());version.setEngineDefinitionId(engine.definitionId());version.setEngineVersion(engine.version());
        version.setStatus(ProcessVersionEntity.Status.PUBLISHED);version.setPublishedBy(actor);
        version.setPublishedAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        version.getProcessDefinition().setActiveProcessVersion(version);
        versions.saveAndFlush(version);
        var receipt=new ProcessPublicationEntity();receipt.setActor(actor);receipt.setRequestKey(key);
        receipt.setProcessDefinition(version.getProcessDefinition());receipt.setProcessVersion(version);receipt.setSourceRevision(command.expectedRevision());
        try {receipts.saveAndFlush(receipt);}
        catch(DataIntegrityViolationException ex) {throw reused();}
        return store.version(id,command.versionId()).orElseThrow();
    }
    private static ApplicationException reused() {return new ApplicationException(ErrorCode.IDEMPOTENCY_KEY_REUSED,"Idempotency-Key đã được dùng cho request khác.");}
}
