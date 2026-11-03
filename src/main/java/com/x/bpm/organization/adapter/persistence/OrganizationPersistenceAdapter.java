package com.x.bpm.organization.adapter.persistence;

import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.organization.application.NewDepartment;
import com.x.bpm.organization.application.NewPosition;
import com.x.bpm.organization.application.OrganizationPort;
import com.x.bpm.organization.application.OrganizationUserRef;
import com.x.bpm.organization.domain.Department;
import com.x.bpm.organization.domain.DepartmentMembership;
import com.x.bpm.organization.domain.OrganizationStatus;
import com.x.bpm.organization.domain.Position;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrganizationPersistenceAdapter implements OrganizationPort {
    private final DepartmentJpaRepository departments;
    private final PositionJpaRepository positions;
    private final DepartmentMemberJpaRepository memberships;
    private final EntityManager entityManager;

    public OrganizationPersistenceAdapter(DepartmentJpaRepository departments,
            PositionJpaRepository positions, DepartmentMemberJpaRepository memberships,
            EntityManager entityManager) {
        this.departments = departments;
        this.positions = positions;
        this.memberships = memberships;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> findAllDepartments() {
        return departments.findAllDetailed().stream().map(OrganizationPersistenceAdapter::map).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Department> findDepartmentById(UUID id) {
        return departments.findDetailedById(id).map(OrganizationPersistenceAdapter::map);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Department createDepartment(NewDepartment source) {
        if (departments.existsByCodeIgnoreCase(source.code().strip())) {
            throw new IllegalArgumentException("Department code already exists");
        }
        var entity = new DepartmentEntity();
        apply(entity, source.code(), source.name(), source.parentId(), source.managerUserId(),
                source.status());
        return map(departments.saveAndFlush(entity));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Department saveDepartment(Department source) {
        var entity = departments.findById(source.id()).orElseThrow();
        requireRevision(entity.getRevision(), source.revision(), "Department");
        apply(entity, source.code(), source.name(), source.parentId(), source.managerUserId(),
                source.status());
        return map(departments.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Position> findAllPositions() {
        return positions.findAllByOrderByCodeAsc().stream().map(OrganizationPersistenceAdapter::map).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Position> findPositionById(UUID id) {
        return positions.findById(id).map(OrganizationPersistenceAdapter::map);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Position createPosition(NewPosition source) {
        if (positions.existsByCodeIgnoreCase(source.code().strip())) {
            throw new IllegalArgumentException("Position code already exists");
        }
        var entity = new PositionEntity();
        apply(entity, source.code(), source.name(), source.description(), source.status());
        return map(positions.saveAndFlush(entity));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Position savePosition(Position source) {
        var entity = positions.findById(source.id()).orElseThrow();
        requireRevision(entity.getRevision(), source.revision(), "Position");
        apply(entity, source.code(), source.name(), source.description(), source.status());
        return map(positions.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DepartmentMembership> findMembershipByUserId(UUID userId) {
        return memberships.findDetailedByUserId(userId).map(OrganizationPersistenceAdapter::map);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DepartmentMembership assignMembership(UUID userId, UUID departmentId, UUID positionId) {
        var entity = memberships.findDetailedByUserId(userId).orElseGet(DepartmentMemberEntity::new);
        entity.setUser(entityManager.getReference(UserEntity.class, userId));
        entity.setDepartment(entityManager.getReference(DepartmentEntity.class, departmentId));
        entity.setPosition(positionId == null ? null
                : entityManager.getReference(PositionEntity.class, positionId));
        entity.setPrimaryDepartment(true);
        return map(memberships.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationUserRef> findUserById(UUID userId) {
        var user = entityManager.find(UserEntity.class, userId);
        return Optional.ofNullable(user).map(value -> new OrganizationUserRef(value.getId(),
                value.getUsername(), value.getDisplayName(),
                value.getStatus() == UserEntity.Status.ACTIVE));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveMembers(UUID departmentId) {
        return memberships.hasMembersWithStatus(departmentId, UserEntity.Status.ACTIVE);
    }

    private void apply(DepartmentEntity entity, String code, String name, UUID parentId,
            UUID managerUserId, OrganizationStatus status) {
        entity.setCode(code.strip());
        entity.setName(name.strip());
        entity.setParent(parentId == null ? null
                : entityManager.getReference(DepartmentEntity.class, parentId));
        entity.setManager(managerUserId == null ? null
                : entityManager.getReference(UserEntity.class, managerUserId));
        entity.setStatus(DepartmentEntity.Status.valueOf(status.name()));
    }

    private static void apply(PositionEntity entity, String code, String name, String description,
            OrganizationStatus status) {
        entity.setCode(code.strip());
        entity.setName(name.strip());
        entity.setDescription(description == null ? null : description.strip());
        entity.setStatus(PositionEntity.Status.valueOf(status.name()));
    }

    private static Department map(DepartmentEntity entity) {
        return new Department(entity.getId(), entity.getCode(), entity.getName(),
                entity.getParent() == null ? null : entity.getParent().getId(),
                entity.getManager() == null ? null : entity.getManager().getId(),
                OrganizationStatus.valueOf(entity.getStatus().name()), entity.getRevision());
    }

    private static Position map(PositionEntity entity) {
        return new Position(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDescription(), OrganizationStatus.valueOf(entity.getStatus().name()),
                entity.getRevision());
    }

    private static DepartmentMembership map(DepartmentMemberEntity entity) {
        return new DepartmentMembership(entity.getId(), entity.getUser().getId(),
                entity.getDepartment().getId(),
                entity.getPosition() == null ? null : entity.getPosition().getId(),
                entity.getRevision());
    }

    private static void requireRevision(long actual, long expected, String resource) {
        if (actual != expected) {
            throw new OptimisticLockingFailureException(resource + " revision is stale");
        }
    }
}
