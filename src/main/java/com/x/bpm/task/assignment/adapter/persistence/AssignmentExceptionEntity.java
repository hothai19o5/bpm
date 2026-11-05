package com.x.bpm.task.assignment.adapter.persistence;

import com.x.bpm.businessrecord.adapter.persistence.BusinessRecordEntity;
import com.x.bpm.shared.adapter.persistence.CreatedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Operational exception metadata, not a second runtime task/state engine. */
@Getter @Setter @Entity
@Table(name = "assignment_exceptions", uniqueConstraints = @UniqueConstraint(name = "uk_assignment_exceptions_task", columnNames = "engine_task_id"),
        indexes = @Index(name = "ix_assignment_exceptions_status", columnList = "status,created_at"))
public class AssignmentExceptionEntity extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, foreignKey = @ForeignKey(name = "fk_assignment_exceptions_record"))
    private BusinessRecordEntity record;
    @Column(name = "engine_task_id", nullable = false, length = 255) private String engineTaskId;
    @Column(name = "activity_id", nullable = false, length = 255) private String activityId;
    @Column(name = "reason_code", nullable = false, length = 100) private String reasonCode;
    @Column(nullable = false, length = 32) private String status = "OPEN";
}
