package com.x.bpm.task.adapter.flowable;

import com.x.bpm.shared.adapter.flowable.FlowableCalls;
import com.x.bpm.shared.application.EngineInput;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import com.x.bpm.task.application.engine.EngineTask;
import com.x.bpm.task.application.engine.TaskEnginePort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FlowableTaskEngineAdapter implements TaskEnginePort {
    private final TaskService taskService;

    public FlowableTaskEngineAdapter(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public List<EngineTask> findActiveByInstance(String instanceId) {
        EngineInput.text(instanceId, "instance ID");
        return FlowableCalls.execute(() -> taskService.createTaskQuery().processInstanceId(instanceId).active()
                .includeIdentityLinks().orderByTaskId().asc().list().stream().map(this::map).toList());
    }

    @Override
    public List<EngineTask> findAssigned(String instanceId, String userId) {
        EngineInput.text(instanceId, "instance ID");
        EngineInput.text(userId, "user ID");
        return FlowableCalls.execute(() -> taskService.createTaskQuery().processInstanceId(instanceId).active()
                .taskAssignee(userId).includeIdentityLinks().orderByTaskId().asc().list().stream()
                .map(this::map).toList());
    }

    @Override
    public List<EngineTask> findCandidates(String instanceId, String userId, Set<String> groupIds) {
        EngineInput.text(instanceId, "instance ID");
        EngineInput.text(userId, "user ID");
        var groups = EngineInput.groups(groupIds);
        // Read explicit identity links instead of consulting Flowable IDM for app group membership.
        return findActiveByInstance(instanceId).stream().filter(task -> task.assignee() == null)
                .filter(task -> isCandidate(task, userId, groups)).toList();
    }

    @Override
    @Transactional
    public EngineTask claim(String taskId, String userId, Set<String> groupIds) {
        EngineInput.text(taskId, "task ID");
        EngineInput.text(userId, "user ID");
        var groups = EngineInput.groups(groupIds);
        return FlowableCalls.execute(() -> {
            var task = requiredTask(taskId);
            if (task.getAssignee() != null) {
                throw new ApplicationException(ErrorCode.TASK_CONFLICT, "Task has already been assigned");
            }
            if (!isCandidate(map(task), userId, groups)) {
                throw new ApplicationException(ErrorCode.FORBIDDEN, "User is not a candidate for this task");
            }
            taskService.claim(taskId, userId);
            return map(requiredTask(taskId));
        });
    }

    @Override
    @Transactional
    public void complete(String taskId, String userId, Map<String, Object> variables) {
        EngineInput.text(taskId, "task ID");
        EngineInput.text(userId, "user ID");
        var values = EngineInput.variables(variables);
        FlowableCalls.execute(() -> {
            var task = requiredTask(taskId);
            if (!userId.equals(task.getAssignee())) {
                throw new ApplicationException(ErrorCode.FORBIDDEN, "Only the assigned user can complete this task");
            }
            taskService.complete(taskId, userId, values);
            return null;
        });
    }

    private Task requiredTask(String taskId) {
        var task = taskService.createTaskQuery().taskId(taskId).active().includeIdentityLinks().singleResult();
        if (task == null) {
            throw new ApplicationException(ErrorCode.NOT_FOUND, "Active task was not found");
        }
        return task;
    }

    private static boolean isCandidate(EngineTask task, String userId, Set<String> groups) {
        return task.candidateUserIds().contains(userId)
                || task.candidateGroupIds().stream().anyMatch(groups::contains);
    }

    private EngineTask map(Task task) {
        var links = task.getIdentityLinks();
        var users = links.stream().filter(link -> "candidate".equals(link.getType()) && link.getUserId() != null)
                .map(link -> link.getUserId()).collect(Collectors.toSet());
        var groups = links.stream().filter(link -> "candidate".equals(link.getType()) && link.getGroupId() != null)
                .map(link -> link.getGroupId()).collect(Collectors.toSet());
        return new EngineTask(task.getId(), task.getProcessInstanceId(), task.getProcessDefinitionId(),
                task.getTaskDefinitionKey(), task.getName(), task.getAssignee(), users, groups,
                task.getCreateTime() == null ? null : task.getCreateTime().toInstant());
    }
}
