package com.x.bpm.task.assignment.adapter.persistence;

import com.x.bpm.audit.adapter.persistence.AuditEventEntity;
import com.x.bpm.businessrecord.adapter.persistence.BusinessRecordEntity;
import com.x.bpm.shared.application.*;
import com.x.bpm.shared.error.*;
import com.x.bpm.task.assignment.application.*;
import com.x.bpm.task.assignment.domain.AssignmentStrategy;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaRuntimeAssignmentStore implements RuntimeAssignmentStore {
    private final EntityManager entities;
    public JpaRuntimeAssignmentStore(EntityManager entities) { this.entities = entities; }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public RuntimeAssignmentContext context(UUID recordId, String definitionId, String instanceId, String activityId) {
        var record = entities.find(BusinessRecordEntity.class, recordId);
        if (record == null || !definitionId.equals(record.getProcessVersion().getEngineDefinitionId())
                || record.getRequesterDepartment() == null
                || (record.getEngineInstanceId() == null ? record.getStatus() != BusinessRecordEntity.Status.DRAFT
                    : !instanceId.equals(record.getEngineInstanceId())))
            throw new IllegalStateException("Engine task does not match its pinned business record");
        var raw = record.getProcessVersion().getStepConfig().get(activityId);
        Map<?,?> step = raw instanceof Map<?,?> value ? value : Map.of();
        boolean correction = step.get("actions") instanceof List<?> actions && actions.equals(List.of("RESUBMIT"));
        AssignmentRequest request = null;
        try {
            if (step.get("assignment") instanceof Map<?,?> assignment) {
                request = new AssignmentRequest(AssignmentStrategy.valueOf((String) assignment.get("strategy")),
                        record.getRequester().getId(), record.getRequesterDepartment().getId(),
                        id(assignment.get("specificUserId")), id(assignment.get("departmentId")), id(assignment.get("roleId")), "task.complete");
            }
        } catch (IllegalArgumentException | ClassCastException | NullPointerException invalid) {
            // Published config should be valid; fail closed if a legacy artifact is incomplete.
        }
        return new RuntimeAssignmentContext(recordId, record.getRequester().getId(), record.getRequesterDepartment().getId(), request, correction);
    }

    private UUID id(Object value) { return value == null ? null : UUID.fromString((String) value); }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void journal(String taskId, String activityId, RuntimeAssignmentContext context,
            AssignmentResolution resolution, String candidateGroupId) {
        if (resolution.failure() != null) {
            var exception = new AssignmentExceptionEntity();
            exception.setRecord(entities.getReference(BusinessRecordEntity.class, context.recordId()));
            exception.setEngineTaskId(taskId); exception.setActivityId(activityId);
            exception.setReasonCode(resolution.failure().name()); entities.persist(exception);
        }
        var audit = new AuditEventEntity();
        audit.setSource("SYSTEM"); audit.setActor(null);
        audit.setAction(resolution.failure() != null ? "TASK_ASSIGNMENT_FAILED"
                : resolution.assigneeUserId() != null ? "TASK_ASSIGNED" : "TASK_CANDIDATES_CREATED");
        audit.setEntityType("ENGINE_TASK"); audit.setEntityId(taskId);
        audit.setCorrelationId(MDC.get("correlationId")); audit.setResult(resolution.failure() == null ? "SUCCESS" : "WAITING");
        var diff = new LinkedHashMap<String,Object>();
        diff.put("actorType", "SYSTEM"); diff.put("recordId", context.recordId().toString()); diff.put("activityId", activityId);
        diff.put("requesterDepartmentId", context.requesterDepartmentId().toString());
        if (resolution.assigneeUserId() != null) diff.put("assigneeId", resolution.assigneeUserId().toString());
        if (candidateGroupId != null) {
            diff.put("candidateGroupId", candidateGroupId);
            diff.put("eligibleAtCreation", resolution.candidateUserIds().stream().map(UUID::toString).sorted().toList());
        }
        if (resolution.failure() != null) diff.put("reasonCode", resolution.failure().name());
        if (context.requesterCorrection()) diff.put("policy", "REQUESTER_CORRECTION");
        audit.setDiff(diff); entities.persist(audit);
        // Failures in exception/audit persistence must roll back engine creation too.
        entities.flush();
    }

    @Override @Transactional(readOnly = true)
    public PageResult<AssignmentExceptionView> exceptions(PageQuery page) {
        if (!page.sort().equals("createdAt,desc") || page.offset() > Integer.MAX_VALUE)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Phân trang ngoại lệ không hợp lệ.");
        long total = entities.createQuery("select count(e) from AssignmentExceptionEntity e where e.status='OPEN'", Long.class).getSingleResult();
        var rows = entities.createQuery("select e from AssignmentExceptionEntity e join fetch e.record where e.status='OPEN' order by e.createdAt desc,e.id desc", AssignmentExceptionEntity.class)
                .setFirstResult((int)page.offset()).setMaxResults(page.size()).getResultList();
        return new PageResult<>(rows.stream().map(e -> new AssignmentExceptionView(e.getId(), e.getRecord().getId(), e.getRecord().getBusinessKey(),
                e.getEngineTaskId(), e.getActivityId(), e.getReasonCode(), e.getStatus(), e.getCreatedAt())).toList(), page.page(), page.size(), total);
    }
}
