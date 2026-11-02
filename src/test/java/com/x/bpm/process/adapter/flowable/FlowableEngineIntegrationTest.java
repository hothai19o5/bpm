package com.x.bpm.process.adapter.flowable;

import com.x.bpm.process.application.engine.EngineDeployment;
import com.x.bpm.process.application.engine.EngineProcessDefinition;
import com.x.bpm.process.application.engine.EngineProcessInstance;
import com.x.bpm.process.application.engine.ProcessEnginePort;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import com.x.bpm.support.IsolatedPostgresTest;
import com.x.bpm.task.application.engine.EngineTask;
import com.x.bpm.task.application.engine.TaskEnginePort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** No enclosing test transaction: each adapter mutation commits to real PostgreSQL. */
@IsolatedPostgresTest
class FlowableEngineIntegrationTest {
    @Autowired private ProcessEnginePort processes;
    @Autowired private TaskEnginePort tasks;
    // Engine access is confined to test cleanup; all scenario actions use application ports.
    @Autowired private RepositoryService repositoryService;
    private final List<String> deployments = new ArrayList<>();

    @AfterEach
    void removeOnlyDeploymentsCreatedByThisTest() {
        for (int index = deployments.size() - 1; index >= 0; index--) {
            repositoryService.deleteDeployment(deployments.get(index), true);
        }
    }

    @ParameterizedTest
    @CsvSource({"APPROVE,approvedEnd", "REJECT,rejectedEnd"})
    void directAssigneeCompletesBothXorBranchesAndHistory(String outcome, String endActivity) throws IOException {
        var definition = deploy("p15-direct-review.bpmn20.xml");
        var instance = processes.start(definition.definitionId(), businessKey(), Map.of("reviewerId", "reviewer-1"));

        assertRunning(instance);
        var task = onlyTask(instance);
        assertThat(task.definitionId()).isEqualTo(definition.definitionId());
        assertThat(task.taskDefinitionKey()).isEqualTo("reviewTask");
        assertThat(task.assignee()).isEqualTo("reviewer-1");
        assertThat(task.createdAt()).isNotNull();
        assertThat(tasks.findAssigned(instance.instanceId(), "reviewer-1"))
                .extracting(EngineTask::taskId).containsExactly(task.taskId());
        assertThat(tasks.findAssigned(instance.instanceId(), "other-user")).isEmpty();
        assertThat(tasks.findCandidates(instance.instanceId(), "reviewer-1", Set.of())).isEmpty();

        var waitingHistory = processes.findHistory(instance.instanceId()).orElseThrow();
        assertThat(waitingHistory.endedAt()).isNull();
        assertThat(waitingHistory.tasks()).singleElement().satisfies(history -> {
            assertThat(history.taskId()).isEqualTo(task.taskId());
            assertThat(history.endedAt()).isNull();
        });

        tasks.complete(task.taskId(), "reviewer-1", Map.of("outcome", outcome));
        assertFinished(instance, task, "reviewer-1", endActivity);
    }

    @ParameterizedTest
    @CsvSource({"APPROVE,approvedEnd", "REJECT,rejectedEnd"})
    void candidateGroupClaimsQueueAndCompletesBothBranches(String outcome, String endActivity) throws IOException {
        var instance = startQueue();
        var task = onlyTask(instance);
        assertThat(task.assignee()).isNull();
        assertThat(task.candidateUserIds()).containsExactly("candidate-user");
        assertThat(task.candidateGroupIds()).containsExactly("reviewers");
        assertThat(tasks.findCandidates(instance.instanceId(), "group-member", Set.of("reviewers")))
                .extracting(EngineTask::taskId).containsExactly(task.taskId());
        assertThat(tasks.findCandidates(instance.instanceId(), "outsider", Set.of("unrelated"))).isEmpty();

        var claimed = tasks.claim(task.taskId(), "group-member", Set.of("reviewers"));
        assertThat(claimed.assignee()).isEqualTo("group-member");
        assertThat(tasks.findAssigned(instance.instanceId(), "group-member"))
                .extracting(EngineTask::taskId).containsExactly(task.taskId());
        assertThat(tasks.findCandidates(instance.instanceId(), "candidate-user", Set.of("reviewers"))).isEmpty();

        tasks.complete(task.taskId(), "group-member", Map.of("outcome", outcome));
        assertFinished(instance, task, "group-member", endActivity);
    }

    @Test
    void explicitCandidateUserCanClaimWithoutGroupMembership() throws IOException {
        var instance = startQueue();
        var task = onlyTask(instance);
        assertThat(tasks.findCandidates(instance.instanceId(), "candidate-user", Set.of()))
                .extracting(EngineTask::taskId).containsExactly(task.taskId());
        tasks.claim(task.taskId(), "candidate-user", Set.of());
        tasks.complete(task.taskId(), "candidate-user", Map.of("outcome", "APPROVE"));
        assertFinished(instance, task, "candidate-user", "approvedEnd");
    }

    @Test
    void deniesNonCandidateUnclaimedCompletionAndWrongAssignee() throws IOException {
        var instance = startQueue();
        var task = onlyTask(instance);
        assertError(ErrorCode.FORBIDDEN, () -> tasks.claim(task.taskId(), "outsider", Set.of("other")));
        assertError(ErrorCode.FORBIDDEN,
                () -> tasks.complete(task.taskId(), "candidate-user", Map.of("outcome", "APPROVE")));
        assertThat(onlyTask(instance).assignee()).isNull();

        tasks.claim(task.taskId(), "candidate-user", Set.of());
        assertError(ErrorCode.TASK_CONFLICT, () -> tasks.claim(task.taskId(), "group-member", Set.of("reviewers")));
        assertError(ErrorCode.FORBIDDEN,
                () -> tasks.complete(task.taskId(), "outsider", Map.of("outcome", "REJECT")));
        assertThat(onlyTask(instance).assignee()).isEqualTo("candidate-user");
        tasks.complete(task.taskId(), "candidate-user", Map.of("outcome", "APPROVE"));
        assertFinished(instance, task, "candidate-user", "approvedEnd");
    }

    @Test
    void pinsDefinitionIdEvenAfterNewVersionOfSameKeyIsDeployed() throws IOException {
        String resource = "p15-direct-review.bpmn20.xml";
        var first = deploy(resource);
        var secondDeployment = deployXml(resource, xml(resource).replace("P1.5 Direct Review", "P1.5 Direct Review v2"));
        var second = secondDeployment.definitions().getFirst();
        assertThat(second.key()).isEqualTo(first.key());
        assertThat(second.version()).isEqualTo(first.version() + 1);
        assertThat(second.definitionId()).isNotEqualTo(first.definitionId());

        var pinned = processes.start(first.definitionId(), businessKey(), Map.of("reviewerId", "reviewer-1"));
        var latest = processes.start(second.definitionId(), businessKey(), Map.of("reviewerId", "reviewer-2"));
        assertThat(pinned.definitionId()).isEqualTo(first.definitionId());
        assertThat(latest.definitionId()).isEqualTo(second.definitionId());
        assertThat(onlyTask(pinned).definitionId()).isEqualTo(first.definitionId());
        assertThat(onlyTask(latest).definitionId()).isEqualTo(second.definitionId());
        tasks.complete(onlyTask(pinned).taskId(), "reviewer-1", Map.of("outcome", "APPROVE"));
        assertThat(processes.findRunningInstance(latest.instanceId())).isPresent();
        tasks.complete(onlyTask(latest).taskId(), "reviewer-2", Map.of("outcome", "REJECT"));
        assertThat(processes.findHistory(pinned.instanceId()).orElseThrow().definitionId()).isEqualTo(first.definitionId());
        assertThat(processes.findHistory(latest.instanceId()).orElseThrow().definitionId()).isEqualTo(second.definitionId());
    }

    @Test
    void queryIsolatesInstancesAndBusinessKeys() throws IOException {
        var definition = deploy("p15-direct-review.bpmn20.xml");
        var first = processes.start(definition.definitionId(), businessKey(), Map.of("reviewerId", "reviewer-1"));
        var second = processes.start(definition.definitionId(), businessKey(), Map.of("reviewerId", "reviewer-1"));
        assertRunning(first);
        assertRunning(second);
        assertThat(tasks.findAssigned(first.instanceId(), "reviewer-1"))
                .extracting(EngineTask::instanceId).containsExactly(first.instanceId());
        assertThat(tasks.findCandidates(first.instanceId(), "reviewer-1", Set.of())).isEmpty();
        assertThat(processes.findRunningInstancesByBusinessKey(businessKey())).isEmpty();
        assertThat(processes.findRunningInstance("missing-instance")).isEmpty();
        assertThat(processes.findHistory("missing-instance")).isEmpty();
    }

    @Test
    void missingEngineIdsReturnApplicationErrorsAndKeyIsNotAcceptedAsDefinitionId() throws IOException {
        var definition = deploy("p15-direct-review.bpmn20.xml");
        assertError(ErrorCode.NOT_FOUND,
                () -> processes.start(definition.key(), businessKey(), Map.of("reviewerId", "reviewer-1")));
        assertError(ErrorCode.NOT_FOUND, () -> tasks.claim("missing-task", "reviewer-1", Set.of()));
        assertError(ErrorCode.NOT_FOUND, () -> tasks.complete("missing-task", "reviewer-1", Map.of()));
    }

    @Test
    void refusesArbitraryJavaObjectsBeforeStartingAnInstance() throws IOException {
        var definition = deploy("p15-direct-review.bpmn20.xml");
        var key = businessKey();
        assertError(ErrorCode.INVALID_REQUEST, () -> processes.start(definition.definitionId(), key,
                Map.of("reviewerId", "reviewer-1", "payload", new Object())));
        assertThat(processes.findRunningInstancesByBusinessKey(key)).isEmpty();
    }

    private EngineProcessInstance startQueue() throws IOException {
        var definition = deploy("p15-queue-review.bpmn20.xml");
        return processes.start(definition.definitionId(), businessKey(),
                Map.of("candidateUserId", "candidate-user", "candidateGroupId", "reviewers"));
    }

    private EngineProcessDefinition deploy(String resource) throws IOException {
        var deployment = deployXml(resource, xml(resource));
        assertThat(deployment.definitions()).hasSize(1);
        var definition = deployment.definitions().getFirst();
        assertThat(definition.deploymentId()).isEqualTo(deployment.deploymentId());
        return definition;
    }

    private EngineDeployment deployXml(String resource, String xml) {
        var deployment = processes.deploy("P1.5 " + UUID.randomUUID(), resource, xml);
        deployments.add(deployment.deploymentId());
        return deployment;
    }

    private String xml(String resource) throws IOException {
        return new ClassPathResource("fixtures/" + resource).getContentAsString(StandardCharsets.UTF_8);
    }

    private String businessKey() {
        // Application record ID stand-in, no business schema required in P1.5.
        return "p15-record-" + UUID.randomUUID();
    }

    private EngineTask onlyTask(EngineProcessInstance instance) {
        var active = tasks.findActiveByInstance(instance.instanceId());
        assertThat(active).hasSize(1);
        return active.getFirst();
    }

    private void assertRunning(EngineProcessInstance instance) {
        assertThat(instance.startedAt()).isNotNull();
        assertThat(instance.suspended()).isFalse();
        assertThat(processes.findRunningInstance(instance.instanceId())).contains(instance);
        assertThat(processes.findRunningInstancesByBusinessKey(instance.businessKey()))
                .extracting(EngineProcessInstance::instanceId).containsExactly(instance.instanceId());
    }

    private void assertFinished(EngineProcessInstance instance, EngineTask task, String user, String endActivity) {
        assertThat(tasks.findActiveByInstance(instance.instanceId())).isEmpty();
        assertThat(tasks.findAssigned(instance.instanceId(), user)).isEmpty();
        assertThat(processes.findRunningInstance(instance.instanceId())).isEmpty();
        assertThat(processes.findRunningInstancesByBusinessKey(instance.businessKey())).isEmpty();
        var history = processes.findHistory(instance.instanceId()).orElseThrow();
        assertThat(history.instanceId()).isEqualTo(instance.instanceId());
        assertThat(history.definitionId()).isEqualTo(instance.definitionId());
        assertThat(history.businessKey()).isEqualTo(instance.businessKey());
        assertThat(history.startedAt()).isNotNull();
        assertThat(history.endedAt()).isNotNull().isAfterOrEqualTo(history.startedAt());
        assertThat(history.endActivityId()).isEqualTo(endActivity);
        assertThat(history.deleteReason()).isNull();
        assertThat(history.tasks()).singleElement().satisfies(completed -> {
            assertThat(completed.taskId()).isEqualTo(task.taskId());
            assertThat(completed.taskDefinitionKey()).isEqualTo("reviewTask");
            assertThat(completed.assignee()).isEqualTo(user);
            assertThat(completed.completedBy()).isEqualTo(user);
            assertThat(completed.startedAt()).isNotNull();
            assertThat(completed.endedAt()).isNotNull().isAfterOrEqualTo(completed.startedAt());
            assertThat(completed.deleteReason()).isNull();
        });
        // Sequential repeat is rejected; concurrent claims/completions remain P1.6.
        assertError(ErrorCode.NOT_FOUND, () -> tasks.complete(task.taskId(), user, Map.of("outcome", "APPROVE")));
    }

    private void assertError(ErrorCode code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApplicationException.class,
                exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
