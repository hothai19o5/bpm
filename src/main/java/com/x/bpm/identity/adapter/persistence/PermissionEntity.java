package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.shared.adapter.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Lưu danh mục các quyền thao tác trong hệ thống.
 */
@Getter
@Setter
@Entity
@Table(name = "permissions",
        uniqueConstraints = {@UniqueConstraint(name = "uk_permissions_1", columnNames = {"code"})})
public class PermissionEntity extends MutableEntity {
    @Column(name = "code", nullable = false, length = 150)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;
}
