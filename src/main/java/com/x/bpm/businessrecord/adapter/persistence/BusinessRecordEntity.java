package com.x.bpm.businessrecord.adapter.persistence;

import com.x.bpm.form.adapter.persistence.FormVersionEntity;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.organization.adapter.persistence.DepartmentEntity;
import com.x.bpm.process.adapter.persistence.ProcessDefinitionEntity;
import com.x.bpm.process.adapter.persistence.ProcessVersionEntity;
import com.x.bpm.shared.adapter.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Transient;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lưu hồ sơ nghiệp vụ, dữ liệu biểu mẫu và các phiên bản quy trình/biểu mẫu đã gắn.
 */
@Getter
@Setter
@Entity
@Table(name = "business_records",
        uniqueConstraints = {@UniqueConstraint(name = "uk_business_records_1", columnNames = {"business_key"}),
                @UniqueConstraint(name = "uk_business_records_2", columnNames = {"engine_instance_id"})},
        indexes = {@Index(name = "ix_business_records_1", columnList = "requester_id, status, created_at"),
                @Index(name = "ix_business_records_2", columnList = "process_definition_id, status"),
                @Index(name = "ix_business_records_3", columnList = "process_version_id"),
                @Index(name = "ix_business_records_4", columnList = "form_version_id"),
                @Index(name = "ix_business_records_5", columnList = "requester_department_id")})
@Check(constraints = "(status = 'DRAFT' and engine_instance_id is null and submitted_at is null and completed_at is null) or (status in ('IN_PROGRESS', 'CHANGES_REQUESTED') and engine_instance_id is not null and submitted_at is not null and requester_department_id is not null and completed_at is null) or (status in ('COMPLETED', 'REJECTED') and engine_instance_id is not null and submitted_at is not null and requester_department_id is not null and completed_at is not null and completed_at >= submitted_at)")
public class BusinessRecordEntity extends MutableEntity {
    @Transient @Getter(lombok.AccessLevel.NONE) @Setter(lombok.AccessLevel.NONE)
    private Pins pinned;
    @Transient @Getter(lombok.AccessLevel.NONE) @Setter(lombok.AccessLevel.NONE)
    private String pinnedInstance;
    @Transient @Getter(lombok.AccessLevel.NONE) @Setter(lombok.AccessLevel.NONE)
    private java.util.UUID pinnedDepartment;
    @Transient @Getter(lombok.AccessLevel.NONE) @Setter(lombok.AccessLevel.NONE)
    private Instant pinnedSubmittedAt;
    @PostLoad @PostPersist @PostUpdate
    void snapshotPins(){pinned=pins();pinnedInstance=engineInstanceId;pinnedDepartment=requesterDepartment==null?null:requesterDepartment.getId();pinnedSubmittedAt=submittedAt;}
    @PreUpdate
    void requirePinsImmutable(){
        if(pinned!=null&&!pinned.equals(pins()))throw new IllegalStateException("Record identity and version pins are immutable");
        if(pinnedInstance!=null&&(!pinnedInstance.equals(engineInstanceId)||status==Status.DRAFT||!java.util.Objects.equals(pinnedSubmittedAt,submittedAt)
                ||!java.util.Objects.equals(pinnedDepartment,requesterDepartment==null?null:requesterDepartment.getId())))
            throw new IllegalStateException("Submitted record engine and requester department are immutable");
    }
    private Pins pins(){return new Pins(businessKey,processDefinition.getId(),processVersion.getId(),formVersion.getId(),requester.getId());}
    private record Pins(String key,java.util.UUID definitionId,java.util.UUID processVersionId,java.util.UUID formVersionId,java.util.UUID requesterId) { }
    @Column(name = "business_key", nullable = false, length = 100)
    private String businessKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_definition_id", nullable = false, foreignKey = @ForeignKey(name = "fk_business_records_process_definition_id"))
    private ProcessDefinitionEntity processDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_version_id", nullable = false, foreignKey = @ForeignKey(name = "fk_business_records_process_version_id"))
    private ProcessVersionEntity processVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_version_id", nullable = false, foreignKey = @ForeignKey(name = "fk_business_records_form_version_id"))
    private FormVersionEntity formVersion;

    @Column(name = "engine_instance_id", nullable = true, length = 255)
    private String engineInstanceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false, foreignKey = @ForeignKey(name = "fk_business_records_requester_id"))
    private UserEntity requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "requester_department_id", nullable = true, foreignKey = @ForeignKey(name = "fk_business_records_requester_department_id"))
    private DepartmentEntity requesterDepartment;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.DRAFT;

    public enum Status { DRAFT, IN_PROGRESS, CHANGES_REQUESTED, COMPLETED, REJECTED }

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> formData = new LinkedHashMap<>();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Changes on every successful task transition, advancing revision/updatedAt even without a form patch. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_task_action_id", foreignKey = @ForeignKey(name = "fk_business_records_last_task_action"))
    private com.x.bpm.task.adapter.persistence.TaskActionLogEntity lastTaskAction;
}
