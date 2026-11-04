package com.x.bpm.businessrecord.application;
import com.x.bpm.form.application.FormDataView;
import java.util.UUID;
public record RecordDraftTemplateView(UUID processDefinitionId,UUID processVersionId,UUID formVersionId,String processName,FormDataView view) { }
