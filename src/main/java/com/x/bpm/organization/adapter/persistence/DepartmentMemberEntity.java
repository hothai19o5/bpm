package com.x.bpm.organization.adapter.persistence;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Lưu phòng ban chính và chức danh của người dùng.
 */
@Getter
@Setter
@Entity
@Table(name = "department_members",
        uniqueConstraints = {@UniqueConstraint(name = "uk_department_members_1", columnNames = {"user_id"})},
        indexes = {@Index(name = "ix_department_members_1", columnList = "department_id"),
                @Index(name = "ix_department_members_2", columnList = "position_id")})
@Check(constraints = "is_primary = true")
public class DepartmentMemberEntity extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false, foreignKey = @ForeignKey(name = "fk_department_members_department_id"))
    private DepartmentEntity department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_department_members_user_id"))
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "position_id", nullable = true, foreignKey = @ForeignKey(name = "fk_department_members_position_id"))
    private PositionEntity position;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryDepartment = true;
}
