package com.x.bpm.task.assignment.adapter.persistence;

import com.x.bpm.identity.adapter.persistence.RoleEntity;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.organization.adapter.persistence.DepartmentEntity;
import com.x.bpm.task.assignment.application.AssignmentDepartment;
import com.x.bpm.task.assignment.application.AssignmentDirectoryPort;
import com.x.bpm.task.assignment.application.AssignmentRole;
import com.x.bpm.task.assignment.application.AssignmentUser;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read adapter cho resolver; không trả JPA entity qua application boundary. */
@Component
public class JpaAssignmentDirectoryAdapter implements AssignmentDirectoryPort {
    private final EntityManager entityManager;

    public JpaAssignmentDirectoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignmentUser> findUser(UUID userId) {
        return Optional.ofNullable(entityManager.find(UserEntity.class, userId)).map(this::mapUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignmentDepartment> findDepartment(UUID departmentId) {
        var department = entityManager.find(DepartmentEntity.class, departmentId);
        return Optional.ofNullable(department).map(value -> new AssignmentDepartment(value.getId(),
                value.getStatus() == DepartmentEntity.Status.ACTIVE,
                value.getManager() == null ? null : value.getManager().getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignmentRole> findRole(UUID roleId) {
        var role = entityManager.find(RoleEntity.class, roleId);
        return Optional.ofNullable(role).map(value -> new AssignmentRole(value.getId(),
                value.getStatus() == RoleEntity.Status.ACTIVE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentUser> findDepartmentMembers(UUID departmentId) {
        return userIds("""
                select m.user.id from DepartmentMemberEntity m
                where m.department.id = :departmentId
                order by m.user.id
                """, departmentId, null).stream().map(this::requireUser).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentUser> findDepartmentRoleMembers(UUID departmentId, UUID roleId) {
        return userIds("""
                select distinct m.user.id
                from DepartmentMemberEntity m, UserRoleEntity ur
                where m.department.id = :departmentId
                  and ur.user = m.user
                  and ur.role.id = :roleId
                  and ur.role.status = :roleStatus
                order by m.user.id
                """, departmentId, roleId).stream().map(this::requireUser).toList();
    }

    private List<UUID> userIds(String jpql, UUID departmentId, UUID roleId) {
        var query = entityManager.createQuery(jpql, UUID.class)
                .setParameter("departmentId", departmentId);
        if (roleId != null) {
            query.setParameter("roleId", roleId)
                    .setParameter("roleStatus", RoleEntity.Status.ACTIVE);
        }
        return query.getResultList();
    }

    private AssignmentUser requireUser(UUID userId) {
        return findUser(userId).orElseThrow(
                () -> new IllegalStateException("Membership references missing user"));
    }

    private AssignmentUser mapUser(UserEntity user) {
        var departmentIds = entityManager.createQuery("""
                select m.department.id from DepartmentMemberEntity m where m.user.id = :userId
                """, UUID.class).setParameter("userId", user.getId()).getResultList();
        var roleIds = entityManager.createQuery("""
                select ur.role.id from UserRoleEntity ur
                where ur.user.id = :userId and ur.role.status = :roleStatus
                """, UUID.class).setParameter("userId", user.getId())
                .setParameter("roleStatus", RoleEntity.Status.ACTIVE).getResultList();
        var permissionCodes = entityManager.createQuery("""
                select distinct rp.permission.code
                from UserRoleEntity ur, RolePermissionEntity rp
                where ur.user.id = :userId
                  and ur.role = rp.role
                  and ur.role.status = :roleStatus
                """, String.class).setParameter("userId", user.getId())
                .setParameter("roleStatus", RoleEntity.Status.ACTIVE).getResultList();
        return new AssignmentUser(user.getId(), user.getStatus() == UserEntity.Status.ACTIVE,
                departmentIds.isEmpty() ? null : departmentIds.getFirst(),
                copy(roleIds), copy(permissionCodes));
    }

    private static <T> Set<T> copy(List<T> values) {
        return Set.copyOf(new LinkedHashSet<>(values));
    }
}
