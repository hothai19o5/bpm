package com.x.bpm.process.adapter.flowable;

import com.x.bpm.support.IsolatedPostgresTest;
import com.x.bpm.support.P16EngineTestSupport;
import com.x.bpm.support.persistence.TransactionProbe;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in, two JVMs against a persistent isolated DB; see scripts/Verify-P16Restart.ps1. */
@IsolatedPostgresTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
@EnabledIfEnvironmentVariable(named = "P16_RESTART_STAGE", matches = "prepare|resume")
class P16RestartPersistenceTest extends P16EngineTestSupport {
    @Test
    void waitingTasksAndJpaMappingSurviveASeparateJvmAndDatabaseContainerRecreate() throws Exception {
        String runId = System.getenv("P16_RESTART_ID");
        assertThat(runId).matches("[a-f0-9-]{36}");
        if ("prepare".equals(System.getenv("P16_RESTART_STAGE"))) {
            prepare(runId, "direct");
            prepare(runId, "queue");
            prepare(runId, "claimed");
        } else {
            resume(runId, "direct");
            resume(runId, "queue");
            resume(runId, "claimed");
        }
    }

    private void prepare(String runId, String mode) throws Exception {
        String key = key(runId, mode);
        assertThat(readProbe(key)).isNull();
        var deployment = deploy("direct".equals(mode)
                ? "p15-direct-review.bpmn20.xml" : "p15-queue-review.bpmn20.xml");
        inTransaction(() -> {
            var definition = deployment.definitions().getFirst();
            var instance = processes.start(definition.definitionId(), key, "direct".equals(mode)
                    ? Map.of("reviewerId", "reviewer-1", "marker", key)
                    : Map.of("candidateUserId", "candidate-user", "candidateGroupId", "reviewers", "marker", key));
            var task = onlyTask(instance.instanceId());
            if ("claimed".equals(mode)) {
                tasks.claim(task.taskId(), "reviewer-1", Set.of("reviewers"));
            }
            var probe = new TransactionProbe(key, "WAITING");
            probe.link(deployment.deploymentId(), definition.definitionId(), instance.instanceId(), task.taskId());
            entityManager.persist(probe);
            entityManager.flush();
            return null;
        });
        assertWaiting(key, mode);
        System.out.println("P1.6 PREPARED " + key);
        // Deliberately retain this deployment and mapping for the next JVM.
    }

    private void resume(String runId, String mode) {
        String key = key(runId, mode);
        var probe = assertWaiting(key, mode);
        if ("queue".equals(mode)) {
            tasks.claim(probe.getTaskId(), "reviewer-1", Set.of("reviewers"));
        }
        String outcome = "claimed".equals(mode) ? "REJECT" : "APPROVE";
        inTransaction(() -> {
            entityManager.find(TransactionProbe.class, key).setState(outcome);
            entityManager.flush();
            tasks.complete(probe.getTaskId(), "reviewer-1", Map.of("outcome", outcome));
            return null;
        });
        assertThat(readProbe(key).getState()).isEqualTo(outcome);
        assertFinished(probe.getInstanceId(), probe.getTaskId(),
                "REJECT".equals(outcome) ? "rejectedEnd" : "approvedEnd");
        var history = processes.findHistory(probe.getInstanceId()).orElseThrow();
        assertThat(history.businessKey()).isEqualTo(key);
        assertThat(history.definitionId()).isEqualTo(probe.getDefinitionId());
        System.out.println("P1.6 RESUMED " + key + " " + history.endActivityId());
        // Clean only this run, and only after all assertions for this instance passed.
        repository.deleteDeployment(probe.getDeploymentId(), true);
        inTransaction(() -> {
            entityManager.remove(entityManager.find(TransactionProbe.class, key));
            return null;
        });
    }

    private TransactionProbe assertWaiting(String key, String mode) {
        var probe = readProbe(key);
        assertThat(probe).as("Persisted JPA mapping from prepare JVM").isNotNull();
        assertThat(probe.getState()).isEqualTo("WAITING");
        var instance = processes.findRunningInstance(probe.getInstanceId()).orElseThrow();
        assertThat(instance.businessKey()).isEqualTo(key);
        assertThat(instance.definitionId()).isEqualTo(probe.getDefinitionId());
        assertThat(processes.findRunningInstancesByBusinessKey(key))
                .extracting(value -> value.instanceId()).containsExactly(probe.getInstanceId());
        var task = onlyTask(probe.getInstanceId());
        assertThat(task.taskId()).isEqualTo(probe.getTaskId());
        assertThat(task.definitionId()).isEqualTo(probe.getDefinitionId());
        assertThat(task.taskDefinitionKey()).isEqualTo("reviewTask");
        var variables = runtime.getVariables(probe.getInstanceId());
        assertThat(variables).containsEntry("marker", key).doesNotContainKey("outcome");
        if ("queue".equals(mode)) {
            assertThat(task.assignee()).isNull();
            assertThat(tasks.findCandidates(probe.getInstanceId(), "reviewer-1", Set.of("reviewers")))
                    .extracting(value -> value.taskId()).containsExactly(probe.getTaskId());
        } else {
            assertThat(task.assignee()).isEqualTo("reviewer-1");
        }
        if (!"direct".equals(mode)) {
            assertThat(task.candidateUserIds()).containsExactly("candidate-user");
            assertThat(task.candidateGroupIds()).containsExactly("reviewers");
            assertThat(variables)
                    .containsEntry("candidateUserId", "candidate-user").containsEntry("candidateGroupId", "reviewers");
        } else {
            assertThat(variables).containsEntry("reviewerId", "reviewer-1");
        }
        var history = processes.findHistory(probe.getInstanceId()).orElseThrow();
        assertThat(history.endedAt()).isNull();
        assertThat(history.tasks()).singleElement().satisfies(value -> {
            assertThat(value.taskId()).isEqualTo(probe.getTaskId());
            assertThat(value.endedAt()).isNull();
        });
        return probe;
    }

    private static String key(String runId, String mode) {
        return "p16-restart-" + runId + "-" + mode;
    }
}
