package com.x.bpm.businessrecord.application;
import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.authorization.domain.*;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import com.x.bpm.form.application.FormDataService;
import com.x.bpm.form.domain.FormAction;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.process.application.engine.ProcessEnginePort;
import com.x.bpm.shared.application.*;
import com.x.bpm.shared.error.*;
import com.x.bpm.task.assignment.application.AssignmentDirectoryPort;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class SubmitRecordUseCase {
    private final RecordStore store;
    private final RecordSubmissionStore submissions;
    private final RecordTransactionRunner transactions;
    private final CurrentActorProvider actors;
    private final FormDataService forms;
    private final AssignmentDirectoryPort directory;
    private final ProcessEnginePort engine;
    private final RecordEngineData dataEncoding;
    private final AuditEventRecorder audit;
    private final Clock clock;
    public SubmitRecordUseCase(RecordStore store,RecordSubmissionStore submissions,RecordTransactionRunner transactions,
            CurrentActorProvider actors,FormDataService forms,AssignmentDirectoryPort directory,ProcessEnginePort engine,
            RecordEngineData dataEncoding,AuditEventRecorder audit,Clock clock) {
        this.store=store;this.submissions=submissions;this.transactions=transactions;this.actors=actors;this.forms=forms;
        this.directory=directory;this.engine=engine;this.dataEncoding=dataEncoding;this.audit=audit;this.clock=clock;
    }
    public RecordView submit(UUID id,SubmitRecord command,UUID requestKey) {
        var actor=actors.requireCurrentActor();var policy=new AuthorizationPolicy();
        policy.requireGranted(policy.requirePermission(actor,PermissionCatalog.RECORD_CREATE));policy.requireGranted(policy.requirePermission(actor,PermissionCatalog.PROCESS_START));
        if(command==null||command.expectedRevision()==null||command.expectedRevision()<0||command.patch()==null||requestKey==null
                ||command.title()==null||command.title().isBlank()||command.title().length()>300)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,"Thiếu revision, tiêu đề, patch hoặc Idempotency-Key hợp lệ.");
        UUID actorId=UUID.fromString(actor.userId());
        String fingerprint=RequestFingerprint.of(Map.of("recordId",id.toString(),"revision",command.expectedRevision(),"title",command.title(),
                "values",command.patch().values(),"removeFields",command.patch().removeFields().stream().sorted().toList()));
        return transactions.required(()->{
            var record=store.find(id,true).orElseThrow(()->new ApplicationException(ErrorCode.NOT_FOUND,"Không tìm thấy hồ sơ."));
            if(!record.requesterId().equals(actorId))throw new ApplicationException(ErrorCode.FORBIDDEN,"Chỉ người tạo được gửi hồ sơ của mình.");
            if(submissions.replay(actorId,requestKey,fingerprint,id))return RecordViews.render(forms,record,store.pinnedTemplate(record),actor,store.isStartable(record));
            if(record.status()!=BusinessRecord.Status.DRAFT||record.engineInstanceId()!=null)throw new ApplicationException(ErrorCode.REVISION_CONFLICT,"Hồ sơ đã được gửi hoặc không còn là bản nháp.");
            RevisionGuard.requireMatch(command.expectedRevision(),record.revision());
            var template=store.startTemplate(record.processDefinitionId(),record.processVersionId());
            var data=forms.applyPatch(template.form(),record.data(),command.patch(),template.requesterPolicy(),FormAction.SUBMIT,null);
            RecordDraftUseCase.requireAttachmentsLinked(template,data);
            var requester=directory.findUser(actorId).filter(user->user.active()&&user.departmentId()!=null)
                    .orElseThrow(SubmitRecordUseCase::missingDepartment);
            directory.findDepartment(requester.departmentId()).filter(department->department.active()).orElseThrow(SubmitRecordUseCase::missingDepartment);
            Instant submittedAt=Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
            submissions.prepare(id,requester.departmentId(),command.title().strip(),data);
            var variables=Map.<String,Object>of("bpmRecordId",id.toString(),"bpmRequesterId",actor.userId(),"bpmRequesterDepartmentId",requester.departmentId().toString(),
                    "bpmProcessVersionId",record.processVersionId().toString(),"bpmFormVersionId",record.formVersionId().toString(),"bpmFormData",dataEncoding.encode(data),"bpmOutcome","SUBMIT");
            var instance=engine.start(template.engineDefinitionId(),record.businessKey(),variables);
            Instant completedAt=null;
            if(engine.findRunningInstance(instance.instanceId()).isEmpty())completedAt=engine.findHistory(instance.instanceId())
                    .filter(history->history.endedAt()!=null).orElseThrow(()->new ApplicationException(ErrorCode.INTERNAL_ERROR,"Không đọc được kết quả engine.")).endedAt();
            var saved=submissions.finish(id,instance.instanceId(),submittedAt,completedAt);
            submissions.remember(actorId,requestKey,fingerprint,id,instance.instanceId());
            audit.record("RECORD_SUBMITTED","BUSINESS_RECORD",id.toString(),Map.of("processVersionId",record.processVersionId().toString(),"formVersionId",record.formVersionId().toString(),
                    "requesterDepartmentId",requester.departmentId().toString(),"engineDefinitionId",template.engineDefinitionId(),"engineInstanceId",instance.instanceId()));
            return RecordViews.render(forms,saved,template,actor,false);
        });
    }
    private static ApplicationException missingDepartment(){return new ApplicationException(ErrorCode.VALIDATION_FAILED,"Người tạo chưa có phòng ban đang hoạt động.",List.of(new ValidationIssue("requesterDepartment","Cần cấu hình phòng ban chính trước khi gửi.",null)));}
}
