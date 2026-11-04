package com.x.bpm.businessrecord.application;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import com.x.bpm.form.application.FormDataView;
import com.x.bpm.form.domain.FormAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
/** Resource-authorized response with server-filtered form data. */
public record RecordView(UUID id,String businessKey,UUID processDefinitionId,UUID processVersionId,UUID formVersionId,
        UUID requesterId,String title,BusinessRecord.Status status,long revision,Instant updatedAt,FormDataView view,
        List<FormAction> actions,boolean canDelete) { }
