package com.x.bpm.businessrecord.application;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import com.x.bpm.shared.application.PageQuery;
public record RecordQuery(String q,BusinessRecord.Status status,PageQuery page) { }
