package com.x.bpm.process.application.engine;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Internal engine boundary, separate from the application process catalog.
 * Callers supply trusted application data; publishing/start authorization belongs
 * to the application use case. This port does not expose an HTTP endpoint.
 */
public interface ProcessEnginePort {
    EngineDeployment deploy(String name, String resourceName, String bpmnXml);

    /** Definition ID is pinned explicitly; never resolve the latest version by key. */
    EngineProcessInstance start(String definitionId, String businessKey, Map<String, Object> variables);

    Optional<EngineProcessInstance> findRunningInstance(String instanceId);

    List<EngineProcessInstance> findRunningInstancesByBusinessKey(String businessKey);

    /** Includes unfinished tasks while the instance is running; absent if history is unknown. */
    Optional<EngineProcessHistory> findHistory(String instanceId);
}
