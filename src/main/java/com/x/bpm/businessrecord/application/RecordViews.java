package com.x.bpm.businessrecord.application;
import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import com.x.bpm.form.application.FormDataService;
import com.x.bpm.form.domain.*;
import com.x.bpm.identity.domain.ActorContext;
import java.util.*;

/** Render only after a resource/action authorization decision in the calling use case. */
final class RecordViews {
    private RecordViews(){ }
    static RecordView render(FormDataService forms,BusinessRecord r,RecordTemplate t,ActorContext actor,boolean startable) {
        boolean own=actor.userId().equals(r.requesterId().toString());
        boolean draft=own&&r.status()==BusinessRecord.Status.DRAFT;
        boolean save=draft&&actor.hasPermission(PermissionCatalog.RECORD_UPDATE_DRAFT);
        boolean submit=draft&&startable&&actor.hasPermission(PermissionCatalog.RECORD_CREATE)&&actor.hasPermission(PermissionCatalog.PROCESS_START);
        FormFieldPolicy fields=t.requesterPolicy();
        if(!own&&actor.hasPermission(PermissionCatalog.RECORD_READ_ALL)) {
            var access=new LinkedHashMap<String,FormFieldAccess>();((Map<?,?>)t.form().schema().get("properties")).keySet().forEach(field->access.put(field.toString(),FormFieldAccess.READ_ONLY));fields=new FormFieldPolicy(access);
        } else if(!save&&!submit) {
            var access=new LinkedHashMap<String,FormFieldAccess>();fields.access().forEach((field,value)->access.put(field,value==FormFieldAccess.EDITABLE?FormFieldAccess.READ_ONLY:value));fields=new FormFieldPolicy(access);
        }
        var actions=new ArrayList<FormAction>();if(save)actions.add(FormAction.SAVE_DRAFT);if(submit)actions.add(FormAction.SUBMIT);
        return new RecordView(r.id(),r.businessKey(),r.processDefinitionId(),r.processVersionId(),r.formVersionId(),r.requesterId(),r.title(),r.status(),r.revision(),r.updatedAt(),forms.read(t.form(),r.data(),fields),
                List.copyOf(actions),draft&&actor.hasPermission(PermissionCatalog.RECORD_DELETE_DRAFT),draft&&!startable?"Phiên bản quy trình không còn nhận hồ sơ mới. Bản nháp và dữ liệu đã lưu được giữ nguyên.":null);
    }
}
