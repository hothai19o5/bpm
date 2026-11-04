package com.x.bpm.process.application;
public interface ProcessValidationPort {
    ProcessModelInput validateDraft(String processKey, ProcessModelInput input);
    String prepareDeployment(String processKey, ProcessModelInput input);
    ProcessModelInput validate(String processKey, ProcessModelInput input);
}
