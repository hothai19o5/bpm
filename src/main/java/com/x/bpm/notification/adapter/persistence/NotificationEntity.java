package com.x.bpm.notification.adapter.persistence;

import com.x.bpm.businessrecord.adapter.persistence.BusinessRecordEntity;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.adapter.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Lưu thông báo trong ứng dụng và thời điểm người nhận đã đọc.
 */
@Getter
@Setter
@Entity
@Table(name = "notifications",
        indexes = {@Index(name = "ix_notifications_1", columnList = "recipient_id, read_at, created_at"),
                @Index(name = "ix_notifications_2", columnList = "business_record_id")})
@Check(constraints = "read_at is null or read_at >= created_at")
public class NotificationEntity extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_recipient_id"))
    private UserEntity recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "business_record_id", nullable = true, foreignKey = @ForeignKey(name = "fk_notifications_business_record_id"))
    private BusinessRecordEntity businessRecord;

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "engine_task_id", nullable = true, length = 255)
    private String engineTaskId;

    @Column(name = "read_at")
    private Instant readAt;
}
