package com.x.bpm.process.application;
import java.util.Map;
import java.util.UUID;
/** Frozen authoring metadata on the Start Event, separate from approver permissions. */
public record ProcessSubmissionConfig(String elementId,UUID formVersionId,Map<String,String> fieldAccess) {
    public ProcessSubmissionConfig {fieldAccess=Map.copyOf(fieldAccess);}
    public boolean editable(){return fieldAccess.containsValue("EDITABLE");}
}
