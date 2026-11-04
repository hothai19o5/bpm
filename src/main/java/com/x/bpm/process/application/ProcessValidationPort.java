package com.x.bpm.process.application;
public interface ProcessValidationPort {
    String prepareDeployment(String processKey, ProcessModelInput input);
    ProcessModelInput validate(String processKey, ProcessModelInput input);
}
