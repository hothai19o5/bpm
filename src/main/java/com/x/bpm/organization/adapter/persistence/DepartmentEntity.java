package com.x.bpm.organization.adapter.persistence;

import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.adapter.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Lưu phòng ban, quan hệ cha/con và người phụ trách.
 */
@Getter
@Setter
@Entity
@Table(name = "departments",
        uniqueConstraints = {@UniqueConstraint(name = "uk_departments_1", columnNames = {"code"})},
        indexes = {@Index(name = "ix_departments_1", columnList = "parent_id"),
                @Index(name = "ix_departments_2", columnList = "manager_user_id")})
@Check(constraints = "parent_id is null or parent_id <> id")
public class DepartmentEntity extends MutableEntity {
    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "parent_id", nullable = true, foreignKey = @ForeignKey(name = "fk_departments_parent_id"))
    private DepartmentEntity parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "manager_user_id", nullable = true, foreignKey = @ForeignKey(name = "fk_departments_manager_user_id"))
    private UserEntity manager;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.ACTIVE;

    public enum Status { ACTIVE, INACTIVE }
}
