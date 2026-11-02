package com.x.bpm.identity.adapter.persistence;

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
 * Lưu lần đăng nhập, thời hạn hiệu lực và thông tin thu hồi.
 */
@Getter
@Setter
@Entity
@Table(name = "login_grants",
        indexes = {@Index(name = "ix_login_grants_1", columnList = "user_id"),
                @Index(name = "ix_login_grants_2", columnList = "expires_at")})
@Check(constraints = "security_version >= 0 and expires_at > created_at and (revoked_at is null or revoked_at >= created_at)")
public class LoginGrantEntity extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_login_grants_user_id"))
    private UserEntity user;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", nullable = true, length = 500)
    private String revokeReason;
}
