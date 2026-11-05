package com.x.bpm.task.application;

import com.x.bpm.task.application.engine.EngineTask;
import com.x.bpm.task.assignment.application.AssignmentRequest;
import java.util.UUID;

public record BusinessTask(EngineTask engine, UUID recordId, String businessKey, String recordTitle,
        String recordStatus, long recordRevision, UUID requesterId, AssignmentRequest assignment, boolean requesterCorrection) { }
