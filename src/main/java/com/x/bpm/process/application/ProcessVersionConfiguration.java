package com.x.bpm.process.application;

import java.util.Map;
import java.util.UUID;

/** Artifact quản trị; không trả qua danh mục dành cho người khởi tạo/giám sát. */
public record ProcessVersionConfiguration(ProcessVersionSummary metadata, String bpmnXml,
        Map<String, Object> stepConfig, Map<String, Object> formBindings, UUID formVersionId,
        String engineDeploymentId, String engineDefinitionId, Integer engineVersion, String artifactChecksum) { }
