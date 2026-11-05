package com.x.bpm.businessrecord.adapter.persistence;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.adapter.persistence.CreatedEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Getter @Setter @Entity @Immutable
@Table(name="record_submission_receipts",uniqueConstraints=@UniqueConstraint(name="uk_record_submission_actor_key",columnNames={"actor_id","request_key"}))
public class RecordSubmissionReceiptEntity extends CreatedEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id",nullable=false) private UserEntity actor;
    @Column(name="request_key",nullable=false) private UUID requestKey;
    @Column(nullable=false,length=64) private String fingerprint;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="record_id",nullable=false) private BusinessRecordEntity record;
    @Column(name="engine_instance_id",nullable=false,length=255) private String engineInstanceId;
}
