package com.x.bpm.task.application;

import java.time.Instant;
import java.util.UUID;

public record TaskSummary(String id, UUID recordId, String businessKey, String recordTitle,
        String name, String activityId, String assigneeId, Instant createdAt) { }
