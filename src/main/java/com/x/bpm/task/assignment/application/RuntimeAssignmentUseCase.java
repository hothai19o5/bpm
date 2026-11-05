package com.x.bpm.task.assignment.application;

import java.util.UUID;
import java.util.stream.Collectors;

/** Runs synchronously in the engine/application transaction, never advances a task. */
public final class RuntimeAssignmentUseCase {
    private final RuntimeAssignmentStore store;
    private final AssignmentResolver resolver;
    private final AssignmentDirectoryPort directory;

    public RuntimeAssignmentUseCase(RuntimeAssignmentStore store, AssignmentResolver resolver,
            AssignmentDirectoryPort directory) {
        this.store = store;
        this.resolver = resolver;
        this.directory = directory;
    }

    public Decision created(UUID recordId, String taskId, String definitionId, String instanceId, String activityId) {
        var context = store.context(recordId, definitionId, instanceId, activityId);
        var resolution = context.requesterCorrection() ? correction(context)
                : context.request() == null ? AssignmentResolution.failed(AssignmentResolution.Failure.INVALID_CONFIGURATION)
                : resolver.resolve(context.request());
        String group = null;
        if (!resolution.candidateUserIds().isEmpty()) {
            var eligible = resolution.candidateUserIds().stream().filter(id -> directory.findUser(id)
                    .filter(user -> user.active() && user.hasPermission("task.claim")).isPresent()).collect(Collectors.toSet());
            resolution = eligible.isEmpty() ? AssignmentResolution.failed(AssignmentResolution.Failure.EMPTY_CANDIDATE_GROUP)
                    : AssignmentResolution.candidates(eligible);
            if (!eligible.isEmpty()) group = candidateGroup(context.request());
        }
        store.journal(taskId, activityId, context, resolution, group);
        return new Decision(resolution.assigneeUserId(), group);
    }

    private AssignmentResolution correction(RuntimeAssignmentContext context) {
        var user = directory.findUser(context.requesterId()).orElse(null);
        if (user == null) return AssignmentResolution.failed(AssignmentResolution.Failure.USER_NOT_FOUND);
        if (!user.active()) return AssignmentResolution.failed(AssignmentResolution.Failure.USER_INACTIVE);
        if (!user.hasPermission("task.complete")) return AssignmentResolution.failed(AssignmentResolution.Failure.EMPTY_CANDIDATE_GROUP);
        return AssignmentResolution.direct(user.id());
    }

    /** App-owned group namespaces; membership must be rechecked against the directory at claim time. */
    public static String candidateGroup(AssignmentRequest request) {
        return switch (request.strategy()) {
            case DEPARTMENT -> "bpm:department:" + request.departmentId();
            case ROLE -> "bpm:department:" + request.departmentId() + ":role:" + request.roleId();
            default -> throw new IllegalArgumentException("Not a queue strategy");
        };
    }

    public record Decision(UUID assigneeId, String candidateGroupId) { }
}
