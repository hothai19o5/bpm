package com.x.bpm.businessrecord.adapter.persistence;

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
 * Lưu quyền truy cập bổ sung vào hồ sơ, phạm vi và thời hạn hiệu lực.
 */
@Getter
@Setter
@Entity
@Table(name = "record_access_grants",
        indexes = {@Index(name = "ix_record_access_grants_1", columnList = "business_record_id, subject_user_id"),
                @Index(name = "ix_record_access_grants_2", columnList = "subject_user_id, expires_at"),
                @Index(name = "ix_record_access_grants_3", columnList = "granted_by")})
@Check(constraints = "(expires_at is null or expires_at > valid_from) and (revoked_at is null or revoked_at >= valid_from) and length(trim(reason)) > 0")
public class RecordAccessGrantEntity extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_record_id", nullable = false, foreignKey = @ForeignKey(name = "fk_record_access_grants_business_record_id"))
    private BusinessRecordEntity businessRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_record_access_grants_subject_user_id"))
    private UserEntity subject;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "granted_by", nullable = false, foreignKey = @ForeignKey(name = "fk_record_access_grants_granted_by"))
    private UserEntity grantedBy;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
