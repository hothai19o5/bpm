package com.x.bpm.audit.adapter.persistence;

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
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lưu sự kiện kiểm toán, người thực hiện và kết quả; không cập nhật bản ghi đã lưu.
 */
@Getter
@Setter
@Entity
@Table(name = "audit_events",
        indexes = {@Index(name = "ix_audit_events_1", columnList = "entity_type, entity_id, created_at"),
                @Index(name = "ix_audit_events_2", columnList = "actor_id, created_at"),
                @Index(name = "ix_audit_events_3", columnList = "correlation_id")})
@Check(constraints = "length(trim(source)) > 0")
@Immutable
public class AuditEventEntity extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "actor_id", nullable = true, foreignKey = @ForeignKey(name = "fk_audit_events_actor_id"))
    private UserEntity actor;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "action", nullable = false, length = 150)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 255)
    private String entityId;

    @Column(name = "correlation_id", nullable = true, length = 100)
    private String correlationId;

    @Column(name = "result", nullable = false, length = 50)
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> diff = new LinkedHashMap<>();
}
