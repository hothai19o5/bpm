package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.shared.adapter.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Lưu vai trò dùng để nhóm các quyền của hệ thống.
 */
@Getter
@Setter
@Entity
@Table(name = "roles",
        uniqueConstraints = {@UniqueConstraint(name = "uk_roles_1", columnNames = {"code"})})
public class RoleEntity extends MutableEntity {
    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.ACTIVE;

    public enum Status { ACTIVE, INACTIVE }
}
