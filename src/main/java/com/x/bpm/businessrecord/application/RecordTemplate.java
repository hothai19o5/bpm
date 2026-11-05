package com.x.bpm.businessrecord.application;
import com.x.bpm.form.domain.FormVersion;
import com.x.bpm.form.domain.FormFieldPolicy;
import java.util.UUID;
/** Internal pinned configuration, never serialized as an unfiltered API response. */
public record RecordTemplate(UUID processDefinitionId,UUID processVersionId,String processName,FormVersion form,
                             FormFieldPolicy requesterPolicy,String engineDefinitionId) { }
