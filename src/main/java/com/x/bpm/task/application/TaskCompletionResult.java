package com.x.bpm.task.application;

import java.util.UUID;

/** Durable result of one transition; no form values or subsequent task permissions. */
public record TaskCompletionResult(UUID recordId,String completedTaskId,String action,String recordStatus,long recordRevision) { }
