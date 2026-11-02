package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.shared.adapter.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Lưu tài khoản, trạng thái hoạt động và thông tin bảo mật của người dùng.
 */
@Getter
@Setter
@Entity
@Table(name = "users",
        uniqueConstraints = {@UniqueConstraint(name = "uk_users_1", columnNames = {"normalized_username"})})
@Check(constraints = "security_version >= 0 and length(trim(username)) > 0 and length(trim(normalized_username)) > 0")
public class UserEntity extends MutableEntity {
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "normalized_username", nullable = false, length = 100)
    private String normalizedUsername;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.ACTIVE;

    public enum Status { ACTIVE, INACTIVE }

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @PrePersist
    @PreUpdate
    void normalizeUsername() {
        if (username != null) {
            username = username.strip();
            normalizedUsername = username.toLowerCase(Locale.ROOT);
        }
    }
}
