package com.x.bpm.process.adapter.persistence;

import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.process.application.*;
import com.x.bpm.process.domain.ProcessDefinition;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaProcessStore implements ProcessStore {
    private final ProcessDefinitionJpaRepository definitions;
    private final ProcessVersionJpaRepository versions;
    private final EntityManager entities;

    public JpaProcessStore(ProcessDefinitionJpaRepository definitions, ProcessVersionJpaRepository versions,
            EntityManager entities) {
        this.definitions = definitions;
        this.versions = versions;
        this.entities = entities;
    }

    @Override
    @Transactional
    public ProcessArtifact create(CreateProcess command, UUID actorId) {
        var owner = entities.find(UserEntity.class, command.ownerUserId());
        if (owner == null || owner.getStatus() != UserEntity.Status.ACTIVE)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Owner phải là tài khoản đang hoạt động.");
        var definition = new ProcessDefinitionEntity();
        definition.setKey(command.key());
        definition.setName(command.name());
        definition.setDescription(command.description());
        definition.setOwner(owner);
        try { definitions.saveAndFlush(definition); }
        catch (DataIntegrityViolationException ex) {
            throw new ApplicationException(ErrorCode.REVISION_CONFLICT, "Không thể tạo quy trình với key này.");
        }
        var draft = new ProcessVersionEntity();
        draft.setProcessDefinition(definition);
        draft.setVersionNumber(1);
        draft.setCreatedBy(entities.getReference(UserEntity.class, actorId));
        return artifact(versions.saveAndFlush(draft));
    }

    @Override public Optional<ProcessDefinition> definition(UUID id) { return definitions.findById(id).map(this::definition); }
    @Override public Optional<ProcessArtifact> version(UUID definitionId, UUID versionId) {
        return versions.findByIdAndProcessDefinitionId(versionId, definitionId).map(this::artifact);
    }

    @Override public PageResult<ProcessVersionSummary> history(UUID definitionId, PageQuery page) {
        var from = " from ProcessVersionEntity v where v.processDefinition.id = :id";
        var count = entities.createQuery("select count(v)" + from, Long.class).setParameter("id", definitionId).getSingleResult();
        var rows = entities.createQuery("select v" + from + " order by v.versionNumber "
                + (page.sort().endsWith(",asc") ? "asc" : "desc"), ProcessVersionEntity.class)
                .setParameter("id", definitionId).setFirstResult((int) page.offset()).setMaxResults(page.size())
                .getResultList().stream().map(this::summary).toList();
        return new PageResult<>(rows, page.page(), page.size(), count);
    }

    private ProcessDefinition definition(ProcessDefinitionEntity d) {
        return new ProcessDefinition(d.getId(), d.getKey(), d.getName(), d.getDescription(), d.getOwner().getId(),
                ProcessDefinition.Status.valueOf(d.getStatus().name()),
                d.getActiveProcessVersion() == null ? null : d.getActiveProcessVersion().getId(), d.getRevision());
    }

    private ProcessVersionSummary summary(ProcessVersionEntity v) {
        return new ProcessVersionSummary(v.getId(), v.getProcessDefinition().getId(), v.getVersionNumber(),
                ProcessVersionSummary.Status.valueOf(v.getStatus().name()), v.getCreatedBy().getId(),
                v.getPublishedBy() == null ? null : v.getPublishedBy().getId(), v.getPublishedAt(), v.getUpdatedAt(), v.getRevision());
    }

    private ProcessArtifact artifact(ProcessVersionEntity v) {
        return new ProcessArtifact(definition(v.getProcessDefinition()), new ProcessVersionConfiguration(summary(v),
                v.getBpmnXml(), v.getStepConfig(), v.getFormBindings(), v.getFormVersion() == null ? null : v.getFormVersion().getId(),
                v.getEngineDeploymentId(), v.getEngineDefinitionId(), v.getEngineVersion(), v.getArtifactChecksum()));
    }
}
