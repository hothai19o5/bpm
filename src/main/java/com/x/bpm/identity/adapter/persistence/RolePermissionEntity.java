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
 * Liên kết vai trò với các quyền được phép thực hiện.
 */
@Getter
@Setter
@Entity
@Table(name = "role_permissions",
        uniqueConstraints = {@UniqueConstraint(name = "uk_role_permissions_1", columnNames = {"role_id", "permission_id"})},
        indexes = {@Index(name = "ix_role_permissions_1", columnList = "permission_id")})
public class RolePermissionEntity extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_role_permissions_role_id"))
    private RoleEntity role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false, foreignKey = @ForeignKey(name = "fk_role_permissions_permission_id"))
    private PermissionEntity permission;
}
