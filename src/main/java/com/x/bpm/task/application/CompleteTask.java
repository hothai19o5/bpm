package com.x.bpm.task.application;

import com.x.bpm.form.application.FormDataPatch;
import com.x.bpm.form.domain.FormAction;

public record CompleteTask(Long expectedRevision,FormAction action,FormDataPatch patch,String reason) { }
