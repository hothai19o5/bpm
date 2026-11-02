package com.x.bpm.process.adapter.flowable;

import com.x.bpm.process.application.engine.EngineDeployment;
import com.x.bpm.process.application.engine.EngineProcessDefinition;
import com.x.bpm.process.application.engine.EngineProcessHistory;
import com.x.bpm.process.application.engine.EngineProcessInstance;
import com.x.bpm.process.application.engine.EngineTaskHistory;
import com.x.bpm.process.application.engine.ProcessEnginePort;
import com.x.bpm.shared.adapter.flowable.FlowableCalls;
import com.x.bpm.shared.application.EngineInput;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FlowableProcessEngineAdapter implements ProcessEnginePort {
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    public FlowableProcessEngineAdapter(RepositoryService repositoryService, RuntimeService runtimeService,
                                        HistoryService historyService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
    }

    @Override
    @Transactional
    public EngineDeployment deploy(String name, String resourceName, String bpmnXml) {
        EngineInput.text(name, "deployment name");
        EngineInput.text(resourceName, "resource name");
        EngineInput.text(bpmnXml, "BPMN XML");
        if (!resourceName.endsWith(".bpmn20.xml") && !resourceName.endsWith(".bpmn")) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Resource must have a BPMN extension");
        }
        return FlowableCalls.execute(() -> {
            var deployment = repositoryService.createDeployment().name(name)
                    .addString(resourceName, bpmnXml).deploy();
            var definitions = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId()).orderByProcessDefinitionKey().asc().list().stream()
                    .map(definition -> new EngineProcessDefinition(definition.getId(), deployment.getId(),
                            definition.getKey(), definition.getName(), definition.getVersion())).toList();
            if (definitions.isEmpty()) {
                throw new ApplicationException(ErrorCode.INVALID_REQUEST, "BPMN must contain an executable process");
            }
            return new EngineDeployment(deployment.getId(), definitions);
        });
    }

    @Override
    @Transactional
    public EngineProcessInstance start(String definitionId, String businessKey, Map<String, Object> variables) {
        EngineInput.text(definitionId, "definition ID");
        EngineInput.text(businessKey, "business key");
        var values = EngineInput.variables(variables);
        return FlowableCalls.execute(() -> map(runtimeService.startProcessInstanceById(
                definitionId, businessKey, values)));
    }

    @Override
    public Optional<EngineProcessInstance> findRunningInstance(String instanceId) {
        EngineInput.text(instanceId, "instance ID");
        return FlowableCalls.execute(() -> Optional.ofNullable(runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId).singleResult()).map(FlowableProcessEngineAdapter::map));
    }

    @Override
    public List<EngineProcessInstance> findRunningInstancesByBusinessKey(String businessKey) {
        EngineInput.text(businessKey, "business key");
        return FlowableCalls.execute(() -> runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey).orderByProcessInstanceId().asc().list().stream()
                .map(FlowableProcessEngineAdapter::map).toList());
    }

    @Override
    public Optional<EngineProcessHistory> findHistory(String instanceId) {
        EngineInput.text(instanceId, "instance ID");
        return FlowableCalls.execute(() -> {
            var instance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(instanceId).singleResult();
            if (instance == null) {
                return Optional.empty();
            }
            var tasks = historyService.createHistoricTaskInstanceQuery().processInstanceId(instanceId)
                    .orderByHistoricTaskInstanceStartTime().asc().orderByTaskId().asc().list().stream()
                    .map(task -> new EngineTaskHistory(task.getId(), task.getTaskDefinitionKey(), task.getName(),
                            task.getAssignee(), task.getCompletedBy(), instant(task.getStartTime()),
                            instant(task.getEndTime()), task.getDeleteReason())).toList();
            return Optional.of(new EngineProcessHistory(instance.getId(), instance.getProcessDefinitionId(),
                    instance.getBusinessKey(), instant(instance.getStartTime()), instant(instance.getEndTime()),
                    instance.getEndActivityId(), instance.getDeleteReason(), tasks));
        });
    }

    private static EngineProcessInstance map(ProcessInstance instance) {
        return new EngineProcessInstance(instance.getId(), instance.getProcessDefinitionId(),
                instance.getBusinessKey(), instant(instance.getStartTime()), instance.isSuspended());
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
