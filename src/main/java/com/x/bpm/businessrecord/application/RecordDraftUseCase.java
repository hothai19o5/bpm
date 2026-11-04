package com.x.bpm.businessrecord.application;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.authorization.domain.*;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import com.x.bpm.form.application.*;
import com.x.bpm.form.domain.*;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.shared.application.*;
import com.x.bpm.shared.error.*;
import java.util.*;

public final class RecordDraftUseCase {
    private final RecordStore store;
    private final RecordTransactionRunner transactions;
    private final CurrentActorProvider actors;
    private final FormDataService forms;
    private final AuditEventRecorder audit;
    private final AuthorizationPolicy policy=new AuthorizationPolicy();
    public RecordDraftUseCase(RecordStore store,RecordTransactionRunner transactions,CurrentActorProvider actors,FormDataService forms,AuditEventRecorder audit) {
        this.store=store;this.transactions=transactions;this.actors=actors;this.forms=forms;this.audit=audit;
    }
    public RecordDraftTemplateView template(UUID definitionId,UUID versionId) {
        requireCreate();
        if(definitionId==null||versionId==null)throw invalid();
        return transactions.required(()->{
            var t=store.startTemplate(definitionId,versionId);
            return new RecordDraftTemplateView(t.processDefinitionId(),t.processVersionId(),t.form().id(),t.processName(),forms.read(t.form(),Map.of(),t.requesterPolicy()));
        });
    }
    public RecordView create(CreateRecordDraft command) {
        var actor=requireCreate();
        if(command==null||command.processDefinitionId()==null||command.processVersionId()==null||command.patch()==null)throw invalid();
        requireTitle(command.title());
        return transactions.required(()->{
            var template=store.startTemplate(command.processDefinitionId(),command.processVersionId());
            var data=forms.applyPatch(template.form(),Map.of(),command.patch(),template.requesterPolicy(),FormAction.SAVE_DRAFT,null);
            requireAttachmentsLinked(template,data);
            var record=store.create(template,UUID.fromString(actor.userId()),command.title().strip(),data);
            audit.record("RECORD_DRAFT_CREATED","BUSINESS_RECORD",record.id().toString(),Map.of("processVersionId",record.processVersionId().toString(),"formVersionId",record.formVersionId().toString()));
            return view(record,template,actor);
        });
    }
    public RecordView read(UUID id) {
        var actor=actors.requireCurrentActor();
        return transactions.required(()->{
            var record=store.find(id,false).orElseThrow(RecordDraftUseCase::missing);
            policy.requireGranted(policy.canReadRecord(actor,access(record)));
            return view(record,store.pinnedTemplate(record),actor);
        });
    }
    public RecordView save(UUID id,SaveRecordDraft command) {
        var actor=actors.requireCurrentActor();policy.requireGranted(policy.requirePermission(actor,PermissionCatalog.RECORD_UPDATE_DRAFT));
        if(command==null||command.expectedRevision()==null||command.expectedRevision()<0||command.patch()==null)throw invalid();
        requireTitle(command.title());
        return transactions.required(()->{
            var record=lockedOwner(id,actor,command.expectedRevision());
            var template=store.pinnedTemplate(record);
            var data=forms.applyPatch(template.form(),record.data(),command.patch(),template.requesterPolicy(),FormAction.SAVE_DRAFT,null);
            requireAttachmentsLinked(template,data);
            var saved=store.save(id,command.title().strip(),data);
            audit.record("RECORD_DRAFT_SAVED","BUSINESS_RECORD",id.toString(),Map.of("revision",saved.revision()));
            return view(saved,template,actor);
        });
    }
    public void delete(UUID id,Long expectedRevision) {
        var actor=actors.requireCurrentActor();policy.requireGranted(policy.requirePermission(actor,PermissionCatalog.RECORD_DELETE_DRAFT));
        if(expectedRevision==null||expectedRevision<0)throw invalid();
        transactions.required(()->{
            lockedOwner(id,actor,expectedRevision);store.delete(id);
            audit.record("RECORD_DRAFT_DELETED","BUSINESS_RECORD",id.toString(),Map.of());return null;
        });
    }
    public PageResult<RecordSummary> mine(RecordQuery query) {
        var actor=actors.requireCurrentActor();policy.requireGranted(policy.requirePermission(actor,PermissionCatalog.RECORD_READ_OWN));
        String q=query.q()==null?"":query.q().strip().toLowerCase(Locale.ROOT);
        if(q.length()>200||query.page().offset()>Integer.MAX_VALUE||!Set.of("title","businessKey","createdAt","updatedAt").contains(query.page().sort().split(",")[0]))throw invalid();
        return store.mine(UUID.fromString(actor.userId()),new RecordQuery(q,query.status(),query.page()));
    }
    private BusinessRecord lockedOwner(UUID id,ActorContext actor,long revision) {
        var record=store.find(id,true).orElseThrow(RecordDraftUseCase::missing);
        if(!actor.userId().equals(record.requesterId().toString()))throw new ApplicationException(ErrorCode.FORBIDDEN,"Chỉ chủ hồ sơ được thay đổi bản nháp.");
        if(record.status()!=BusinessRecord.Status.DRAFT)throw new ApplicationException(ErrorCode.REVISION_CONFLICT,"Hồ sơ không còn là bản nháp.");
        RevisionGuard.requireMatch(revision,record.revision());return record;
    }
    private RecordView view(BusinessRecord r,RecordTemplate t,ActorContext actor) {
        boolean own=actor.userId().equals(r.requesterId().toString());
        boolean edit=own&&r.status()==BusinessRecord.Status.DRAFT&&actor.hasPermission(PermissionCatalog.RECORD_UPDATE_DRAFT);
        FormFieldPolicy fields=t.requesterPolicy();
        if(!own&&actor.hasPermission(PermissionCatalog.RECORD_READ_ALL)) {
            var access=new LinkedHashMap<String,FormFieldAccess>();
            ((Map<?,?>)t.form().schema().get("properties")).keySet().forEach(field->access.put(field.toString(),FormFieldAccess.READ_ONLY));fields=new FormFieldPolicy(access);
        } else if(!edit) {
            var access=new LinkedHashMap<String,FormFieldAccess>();fields.access().forEach((field,value)->access.put(field,value==FormFieldAccess.EDITABLE?FormFieldAccess.READ_ONLY:value));fields=new FormFieldPolicy(access);
        }
        return new RecordView(r.id(),r.businessKey(),r.processDefinitionId(),r.processVersionId(),r.formVersionId(),r.requesterId(),r.title(),r.status(),r.revision(),r.updatedAt(),forms.read(t.form(),r.data(),fields),
                edit?List.of(FormAction.SAVE_DRAFT):List.of(),own&&r.status()==BusinessRecord.Status.DRAFT&&actor.hasPermission(PermissionCatalog.RECORD_DELETE_DRAFT));
    }
    private ActorContext requireCreate() {
        var actor=actors.requireCurrentActor();policy.requireGranted(policy.requirePermission(actor,PermissionCatalog.RECORD_CREATE));
        policy.requireGranted(policy.requirePermission(actor,PermissionCatalog.PROCESS_START));return actor;
    }
    private static void requireTitle(String title){if(title==null||title.isBlank()||title.length()>300)throw invalid();}
    private static ResourceAccessContext access(BusinessRecord r){return new ResourceAccessContext(r.requesterId().toString(),null,null,Set.of(),Set.of(),r.status()==BusinessRecord.Status.DRAFT);}
    /** Ownership/storage linking will be supplied at P6; opaque UUIDs never count as uploaded files. */
    @SuppressWarnings("unchecked")
    private static void requireAttachmentsLinked(RecordTemplate t,Map<String,Object> data) {
        var fields=(Map<String,Map<String,Object>>)(Map<?,?>)t.form().schema().get("properties");
        for(var entry:fields.entrySet())if("ATTACHMENT".equals(entry.getValue().get("x-field-type"))&&data.get(entry.getKey()) instanceof List<?> ids&&!ids.isEmpty())
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,"Tệp chưa được liên kết với hồ sơ.",List.of(new ValidationIssue(entry.getKey(),"Chỉ nhận tệp đã được tải lên và liên kết qua dịch vụ tệp.",null)));
    }
    private static ApplicationException invalid(){return new ApplicationException(ErrorCode.INVALID_REQUEST,"Dữ liệu hồ sơ, revision hoặc bộ lọc không hợp lệ.");}
    private static ApplicationException missing(){return new ApplicationException(ErrorCode.NOT_FOUND,"Không tìm thấy hồ sơ.");}
}
