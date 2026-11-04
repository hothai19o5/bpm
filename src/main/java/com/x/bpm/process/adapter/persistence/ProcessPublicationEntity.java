package com.x.bpm.process.adapter.persistence;

import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.adapter.persistence.CreatedEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Getter @Setter @Entity @Immutable
@Table(name="process_publications",uniqueConstraints=@UniqueConstraint(name="uk_process_publication_actor_key",columnNames={"actor_id","request_key"}))
public class ProcessPublicationEntity extends CreatedEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id",nullable=false)
    private UserEntity actor;
    @Column(name="request_key",nullable=false) private UUID requestKey;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="process_definition_id",nullable=false)
    private ProcessDefinitionEntity processDefinition;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="process_version_id",nullable=false)
    private ProcessVersionEntity processVersion;
    @Column(name="source_revision",nullable=false) private long sourceRevision;
}
