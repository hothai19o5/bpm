package com.x.bpm.task.assignment.application;

import java.util.UUID;

/** Frozen process policy and requester department captured at submission. */
public record RuntimeAssignmentContext(UUID recordId, UUID requesterId,
        UUID requesterDepartmentId, AssignmentRequest request, boolean requesterCorrection) { }
