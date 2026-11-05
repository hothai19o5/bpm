package com.x.bpm.task.assignment.application;

import java.time.Instant;
import java.util.UUID;

/** Support metadata only; no form data or implied permission to approve. */
public record AssignmentExceptionView(UUID id, UUID recordId, String businessKey,
        String engineTaskId, String activityId, String reasonCode, String status, Instant createdAt) { }
