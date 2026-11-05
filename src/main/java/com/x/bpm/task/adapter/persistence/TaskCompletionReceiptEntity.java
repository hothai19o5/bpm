package com.x.bpm.task.adapter.persistence;

import com.x.bpm.businessrecord.adapter.persistence.BusinessRecordEntity;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.adapter.persistence.CreatedEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Getter @Setter @Entity @Immutable
@Table(name="task_completion_receipts",uniqueConstraints=@UniqueConstraint(name="uk_task_completion_actor_key",columnNames={"actor_id","request_key"}))
public class TaskCompletionReceiptEntity extends CreatedEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id",nullable=false) private UserEntity actor;
    @Column(name="request_key",nullable=false) private UUID requestKey;
    @Column(nullable=false,length=64) private String fingerprint;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="record_id",nullable=false) private BusinessRecordEntity record;
    @Column(name="engine_task_id",nullable=false,length=255) private String engineTaskId;
    @Column(nullable=false,length=100) private String action;
    @Column(name="record_status",nullable=false,length=32) private String recordStatus;
    @Column(name="record_revision",nullable=false) private long recordRevision;
}
