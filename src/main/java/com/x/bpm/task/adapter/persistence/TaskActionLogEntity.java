package com.x.bpm.task.adapter.persistence;

import com.x.bpm.businessrecord.adapter.persistence.BusinessRecordEntity;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.adapter.persistence.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lưu lịch sử thao tác nghiệp vụ trên công việc của engine; không cập nhật bản ghi đã lưu.
 */
@Getter
@Setter
@Entity
@Table(name = "task_action_logs",
        uniqueConstraints = {@UniqueConstraint(name = "uk_task_action_logs_1", columnNames = {"business_record_id", "actor_id", "idempotency_key"})},
        indexes = {@Index(name = "ix_task_action_logs_1", columnList = "business_record_id, created_at"),
                @Index(name = "ix_task_action_logs_2", columnList = "engine_task_id"),
                @Index(name = "ix_task_action_logs_3", columnList = "actor_id")})
@Immutable
public class TaskActionLogEntity extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_record_id", nullable = false, foreignKey = @ForeignKey(name = "fk_task_action_logs_business_record_id"))
    private BusinessRecordEntity businessRecord;

    @Column(name = "engine_task_id", nullable = false, length = 255)
    private String engineTaskId;

    @Column(name = "activity_id", nullable = false, length = 255)
    private String activityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_task_action_logs_actor_id"))
    private UserEntity actor;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "comment", nullable = true, length = 2000)
    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
}
