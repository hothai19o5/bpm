package com.x.bpm.task.application;

import com.x.bpm.form.application.FormDataView;
import com.x.bpm.form.domain.FormAction;
import java.util.List;

public record TaskDetail(TaskSummary task, String recordStatus, long recordRevision,
        FormDataView view, boolean canClaim, List<FormAction> actions) { }
