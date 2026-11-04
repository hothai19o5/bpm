package com.x.bpm.process.application;
import java.util.Map;
public record ProcessModelInput(String bpmnXml, Map<String,Object> stepConfig, Map<String,Object> formBindings) { }
