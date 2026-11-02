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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Lưu mã băm của token làm mới, thời hạn và thời điểm đã sử dụng.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_credentials",
        uniqueConstraints = {@UniqueConstraint(name = "uk_refresh_credentials_1", columnNames = {"token_hash"})},
        indexes = {@Index(name = "ix_refresh_credentials_1", columnList = "grant_id"),
                @Index(name = "ix_refresh_credentials_2", columnList = "expires_at")})
@Check(constraints = "token_hash ~ '^[0-9a-f]{64}$' and expires_at > created_at and (consumed_at is null or consumed_at >= created_at)")
public class RefreshCredentialEntity extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refresh_credentials_grant_id"))
    private LoginGrantEntity grant;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;
}
