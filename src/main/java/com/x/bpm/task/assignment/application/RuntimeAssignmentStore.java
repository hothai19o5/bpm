package com.x.bpm.task.assignment.application;

import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import java.util.UUID;

public interface RuntimeAssignmentStore {
    RuntimeAssignmentContext context(UUID recordId, String definitionId, String instanceId, String activityId);
    void journal(String taskId, String activityId, RuntimeAssignmentContext context,
            AssignmentResolution resolution, String candidateGroupId);
    PageResult<AssignmentExceptionView> exceptions(PageQuery page);
}
