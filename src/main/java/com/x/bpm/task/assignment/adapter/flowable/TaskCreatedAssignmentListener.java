package com.x.bpm.task.assignment.adapter.flowable;

import com.x.bpm.task.assignment.application.RuntimeAssignmentUseCase;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.flowable.common.engine.api.delegate.event.*;
import org.flowable.task.service.delegate.DelegateTask;

/** Trusted global hook; never enables task listeners or arbitrary expressions in user XML. */
public final class TaskCreatedAssignmentListener implements FlowableEventListener {
    private final RuntimeAssignmentUseCase assignments;
    public TaskCreatedAssignmentListener(RuntimeAssignmentUseCase assignments) { this.assignments = assignments; }
    @Override public Collection<? extends FlowableEventType> getTypes() { return List.of(FlowableEngineEventType.TASK_CREATED); }
    @Override public void onEvent(FlowableEvent event) {
        if (event.getType() != FlowableEngineEventType.TASK_CREATED
                || !(event instanceof FlowableEntityEvent entity) || !(entity.getEntity() instanceof DelegateTask task)) return;
        Object value = task.getVariable("bpmRecordId");
        // Technical engine adapter tests/deployments have no application record or public runtime API.
        if (value == null) return;
        var decision = assignments.created(UUID.fromString((String)value), task.getId(), task.getProcessDefinitionId(),
                task.getProcessInstanceId(), task.getTaskDefinitionKey());
        if (decision.assigneeId() != null) task.setAssignee(decision.assigneeId().toString());
        if (decision.candidateGroupId() != null) task.addCandidateGroup(decision.candidateGroupId());
    }
    @Override public boolean isFailOnException() { return true; }
    @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
    @Override public String getOnTransaction() { return null; }
}
