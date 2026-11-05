package com.x.bpm.task.application;

import com.x.bpm.form.application.FormDataView;

public record TaskDetail(TaskSummary task, String recordStatus, long recordRevision,
        FormDataView view, boolean canClaim) { }
