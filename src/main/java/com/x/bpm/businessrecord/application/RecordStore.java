package com.x.bpm.businessrecord.application;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import com.x.bpm.shared.application.PageResult;
import java.util.*;
public interface RecordStore {
    RecordTemplate startTemplate(UUID definitionId,UUID versionId);
    RecordTemplate pinnedTemplate(BusinessRecord record);
    boolean isStartable(BusinessRecord record);
    Optional<BusinessRecord> find(UUID id,boolean lock);
    BusinessRecord create(RecordTemplate template,UUID requesterId,String title,Map<String,Object> data);
    BusinessRecord save(UUID id,String title,Map<String,Object> data);
    void delete(UUID id);
    PageResult<RecordSummary> mine(UUID requesterId,RecordQuery query);
}
