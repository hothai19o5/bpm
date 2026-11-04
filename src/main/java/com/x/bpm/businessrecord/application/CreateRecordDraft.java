package com.x.bpm.businessrecord.application;
import com.x.bpm.form.application.FormDataPatch;
import java.util.UUID;
public record CreateRecordDraft(UUID processDefinitionId,UUID processVersionId,String title,FormDataPatch patch) { }
