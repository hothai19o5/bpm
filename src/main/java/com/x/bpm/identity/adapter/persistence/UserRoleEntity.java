package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.shared.adapter.persistence.MutableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Liên kết người dùng với vai trò được cấp.
 */
@Getter
@Setter
@Entity
@Table(name = "user_roles",
        uniqueConstraints = {@UniqueConstraint(name = "uk_user_roles_1", columnNames = {"user_id", "role_id"})},
        indexes = {@Index(name = "ix_user_roles_1", columnList = "role_id")})
public class UserRoleEntity extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_roles_user_id"))
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_roles_role_id"))
    private RoleEntity role;
}
