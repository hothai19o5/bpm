package com.x.bpm.businessrecord.application;

import com.x.bpm.form.domain.*;
import java.util.*;

public interface RecordAttachmentLinks {
    void requireLinked(UUID recordId,FormVersion form,Map<String,Object> data);
    List<AttachmentInfo> visible(UUID recordId,FormFieldPolicy policy,Map<String,Object> data);
}
