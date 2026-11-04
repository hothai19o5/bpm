package com.x.bpm.process.application;
public interface ProcessValidationPort {
    java.util.Optional<ProcessSubmissionConfig> submissionConfig(ProcessModelInput input);
    ProcessModelInput copyToKey(String sourceKey,String targetKey,ProcessModelInput input);
    ProcessModelInput validateDraft(String processKey, ProcessModelInput input);
    String prepareDeployment(String processKey, ProcessModelInput input);
    ProcessModelInput validate(String processKey, ProcessModelInput input);
}
