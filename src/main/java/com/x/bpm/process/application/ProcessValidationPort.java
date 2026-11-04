package com.x.bpm.process.application;
public interface ProcessValidationPort {
    ProcessModelInput copyToKey(String sourceKey,String targetKey,ProcessModelInput input);
    ProcessModelInput validateDraft(String processKey, ProcessModelInput input);
    String prepareDeployment(String processKey, ProcessModelInput input);
    ProcessModelInput validate(String processKey, ProcessModelInput input);
}
