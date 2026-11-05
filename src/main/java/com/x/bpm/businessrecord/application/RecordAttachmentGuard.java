package com.x.bpm.businessrecord.application;

import com.x.bpm.form.domain.FormVersion;
import com.x.bpm.shared.error.*;
import java.util.*;

/** Fail closed until the controlled upload/link service provides ownership validation. */
public final class RecordAttachmentGuard {
    private RecordAttachmentGuard() { }
    @SuppressWarnings("unchecked")
    public static void requireLinked(FormVersion form,Map<String,Object> data) {
        var fields=(Map<String,Map<String,Object>>)(Map<?,?>)form.schema().get("properties");
        for(var entry:fields.entrySet())if("ATTACHMENT".equals(entry.getValue().get("x-field-type"))&&data.get(entry.getKey()) instanceof List<?> ids&&!ids.isEmpty())
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,"Tệp chưa được liên kết với hồ sơ.",List.of(new ValidationIssue(entry.getKey(),"Chỉ nhận tệp đã được tải lên và liên kết qua dịch vụ tệp.",null)));
    }
}
