package com.x.bpm.businessrecord.application;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import java.time.Instant;
import java.util.UUID;
public record RecordSummary(UUID id,String businessKey,String title,BusinessRecord.Status status,long revision,Instant updatedAt) { }
