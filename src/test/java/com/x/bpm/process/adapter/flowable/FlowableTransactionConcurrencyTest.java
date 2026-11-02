package com.x.bpm.process.adapter.flowable;

import com.x.bpm.process.application.engine.EngineDeployment;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import com.x.bpm.support.IsolatedPostgresTest;
import com.x.bpm.support.P16EngineTestSupport;
import com.x.bpm.support.persistence.TransactionProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import javax.sql.DataSource;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IsolatedPostgresTest
class FlowableTransactionConcurrencyTest extends P16EngineTestSupport {
    @Autowired private SpringProcessEngineConfiguration engineConfiguration;
    @Autowired private DataSource dataSource;
    private final List<String> deployments = new ArrayList<>();
    private final List<String> probeIds = new ArrayList<>();

    @AfterEach
    void cleanOnlyThisScenario() {
        for (String deployment : deployments) {
            repository.deleteDeployment(deployment, true);
        }
        inTransaction(() -> {
            for (String id : probeIds) {
                var probe = entityManager.find(TransactionProbe.class, id);
                if (probe != null) entityManager.remove(probe);
            }
            return null;
        });
    }

    @Test
    void engineUsesTheJpaTransactionManagerAndItsDataSource() {
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        var jpa = (JpaTransactionManager) transactionManager;
        assertThat(jpa.getDataSource()).isSameAs(dataSource);
        assertThat(engineConfiguration.getTransactionManager()).isSameAs(transactionManager);
        assertThat(engineConfiguration.getDataSource()).isInstanceOf(TransactionAwareDataSourceProxy.class);
        assertThat(((TransactionAwareDataSourceProxy) engineConfiguration.getDataSource()).getTargetDataSource())
                .isSameAs(dataSource);
    }

    @Test
    void failureAfterJpaFlushAndEngineStartRollsBackBoth() throws Exception {
        var deployment = fixture("p15-direct-review.bpmn20.xml");
        String key = probeId();
        var instanceId = new AtomicReference<String>();
        assertThatThrownBy(() -> inTransaction(() -> {
            insertProbe(key, "STARTED");
            var instance = startDirect(deployment, key);
            instanceId.set(instance.instanceId());
            assertThat(onlyTask(instance.instanceId()).assignee()).isEqualTo("reviewer-1");
            throw new DeliberateFailure();
        })).isInstanceOf(DeliberateFailure.class);

        assertThat(instanceId.get()).isNotBlank();
        assertThat(readProbe(key)).isNull();
        assertThat(processes.findRunningInstancesByBusinessKey(key)).isEmpty();
        assertThat(tasks.findActiveByInstance(instanceId.get())).isEmpty();
        assertThat(processes.findHistory(instanceId.get())).isEmpty();
    }

    @Test
    void failureAfterJpaUpdateAndEngineCompleteRestoresRecordTaskVariablesAndHistory() throws Exception {
        var deployment = fixture("p15-direct-review.bpmn20.xml");
        String key = probeId();
        var instance = inTransaction(() -> {
            insertProbe(key, "WAITING");
            return startDirect(deployment, key);
        });
        String taskId = onlyTask(instance.instanceId()).taskId();
        assertThatThrownBy(() -> inTransaction(() -> {
            entityManager.find(TransactionProbe.class, key).setState("APPROVED");
            entityManager.flush();
            tasks.complete(taskId, "reviewer-1", Map.of("outcome", "APPROVE"));
            assertThat(tasks.findActiveByInstance(instance.instanceId())).isEmpty();
            throw new DeliberateFailure();
        })).isInstanceOf(DeliberateFailure.class);

        assertWaiting(key, instance.instanceId(), taskId);
        // Positive control also proves this restored task remains usable.
        inTransaction(() -> {
            entityManager.find(TransactionProbe.class, key).setState("REJECTED");
            entityManager.flush();
            tasks.complete(taskId, "reviewer-1", Map.of("outcome", "REJECT"));
            return null;
        });
        assertThat(readProbe(key).getState()).isEqualTo("REJECTED");
        assertFinished(instance.instanceId(), taskId, "rejectedEnd");
    }

    @Test
    void failureAfterClaimRollsBackJpaAndLeavesTaskInTheCandidateQueue() throws Exception {
        var deployment = fixture("p15-queue-review.bpmn20.xml");
        var instance = processes.start(deployment.definitions().getFirst().definitionId(), probeId(),
                Map.of("candidateUserId", "candidate-user", "candidateGroupId", "reviewers"));
        String taskId = onlyTask(instance.instanceId()).taskId();
        String key = probeId();
        assertThatThrownBy(() -> inTransaction(() -> {
            insertProbe(key, "CLAIMED");
            assertThat(tasks.claim(taskId, "reviewer-1", Set.of("reviewers")).assignee())
                    .isEqualTo("reviewer-1");
            throw new DeliberateFailure();
        })).isInstanceOf(DeliberateFailure.class);
        assertThat(readProbe(key)).isNull();
        assertThat(onlyTask(instance.instanceId()).assignee()).isNull();
        assertThat(tasks.findCandidates(instance.instanceId(), "reviewer-2", Set.of("reviewers")))
                .extracting(task -> task.taskId()).containsExactly(taskId);
        assertThat(processes.findHistory(instance.instanceId()).orElseThrow().tasks())
                .singleElement().satisfies(task -> {
                    assertThat(task.assignee()).isNull();
                    assertThat(task.endedAt()).isNull();
                });
    }

    @Test
    void engineFailureAlsoRollsBackAnAlreadyFlushedJpaUpdate() throws Exception {
        var instance = startDirect(fixture("p15-direct-review.bpmn20.xml"), probeId());
        String taskId = onlyTask(instance.instanceId()).taskId();
        String key = probeId();
        inTransaction(() -> { insertProbe(key, "WAITING"); return null; });
        assertThatThrownBy(() -> inTransaction(() -> {
            entityManager.find(TransactionProbe.class, key).setState("INVALID");
            entityManager.flush();
            // Neither XOR condition matches: the failure happens inside the engine.
            tasks.complete(taskId, "reviewer-1", Map.of("outcome", "NO_MATCH"));
            return null;
        })).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.code()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        assertWaiting(key, instance.instanceId(), taskId);
    }

    @RepeatedTest(3)
    void concurrentClaimsHaveOneOwnerAndOnlyTheWinnersJpaWriteCommits() throws Exception {
        var deployment = fixture("p15-queue-review.bpmn20.xml");
        var instance = processes.start(deployment.definitions().getFirst().definitionId(), probeId(),
                Map.of("candidateUserId", "candidate-user", "candidateGroupId", "reviewers"));
        String taskId = onlyTask(instance.instanceId()).taskId();
        var results = race(instance.instanceId(), taskId, null, index ->
                tasks.claim(taskId, "reviewer-" + (index + 1), Set.of("reviewers")));
        int winner = assertOneWinner(results);
        assertThat(onlyTask(instance.instanceId()).assignee()).isEqualTo("reviewer-" + (winner + 1));
        assertThat(processes.findHistory(instance.instanceId()).orElseThrow().tasks())
                .singleElement().satisfies(task -> assertThat(task.endedAt()).isNull());
    }

    @RepeatedTest(3)
    void concurrentOppositeCompletionsProduceExactlyOneEndAndOneJpaResult() throws Exception {
        var instance = startDirect(fixture("p15-direct-review.bpmn20.xml"), probeId());
        String taskId = onlyTask(instance.instanceId()).taskId();
        var results = race(instance.instanceId(), taskId, "reviewer-1", index -> tasks.complete(taskId, "reviewer-1",
                Map.of("outcome", index == 0 ? "APPROVE" : "REJECT")));
        int winner = assertOneWinner(results);
        assertFinished(instance.instanceId(), taskId, winner == 0 ? "approvedEnd" : "rejectedEnd");
    }

    private void assertWaiting(String key, String instanceId, String taskId) {
        assertThat(readProbe(key).getState()).isEqualTo("WAITING");
        assertThat(processes.findRunningInstance(instanceId)).isPresent();
        assertThat(onlyTask(instanceId).taskId()).isEqualTo(taskId);
        assertThat(runtime.getVariables(instanceId)).doesNotContainKey("outcome");
        var history = processes.findHistory(instanceId).orElseThrow();
        assertThat(history.endedAt()).isNull();
        assertThat(history.tasks()).singleElement().satisfies(task -> {
            assertThat(task.taskId()).isEqualTo(taskId);
            assertThat(task.endedAt()).isNull();
            assertThat(task.completedBy()).isNull();
        });
    }

    private List<Attempt> race(String instanceId, String taskId, String expectedAssignee,
                               IntConsumer operation) throws Exception {
        var barrier = new CyclicBarrier(2);
        // Allocate identifiers before starting threads; cleanup lists stay on the test thread.
        var ids = List.of(probeId(), probeId());
        var executor = Executors.newFixedThreadPool(2);
        List<Future<Attempt>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                final int worker = index;
                Callable<Attempt> action = () -> {
                    var backendPid = new AtomicReference<Number>();
                    try {
                        inTransaction(() -> {
                            insertProbe(ids.get(worker), "WINNER");
                            backendPid.set((Number) entityManager.createNativeQuery("select pg_backend_pid()")
                                    .getSingleResult());
                            var snapshot = onlyTask(instanceId);
                            assertThat(snapshot.taskId()).isEqualTo(taskId);
                            assertThat(snapshot.assignee()).isEqualTo(expectedAssignee);
                            await(barrier);
                            operation.accept(worker);
                            return null;
                        });
                        return new Attempt(ids.get(worker), backendPid.get(), null);
                    } catch (ApplicationException error) {
                        return new Attempt(ids.get(worker), backendPid.get(), error.code());
                    }
                };
                futures.add(executor.submit(action));
            }
            var first = futures.get(0).get(45, TimeUnit.SECONDS);
            var second = futures.get(1).get(45, TimeUnit.SECONDS);
            assertThat(first.backendPid()).isNotNull().isNotEqualTo(second.backendPid());
            return List.of(first, second);
        } finally {
            futures.forEach(future -> future.cancel(true));
            executor.shutdownNow();
            assertThat(executor.awaitTermination(40, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int assertOneWinner(List<Attempt> attempts) {
        assertThat(attempts.stream().filter(attempt -> attempt.error() == null).count()).isEqualTo(1);
        int winner = attempts.getFirst().error() == null ? 0 : 1;
        var loser = attempts.get(1 - winner);
        assertThat(loser.error()).isIn(ErrorCode.TASK_CONFLICT, ErrorCode.NOT_FOUND);
        assertThat(readProbe(attempts.get(winner).probeId()).getState()).isEqualTo("WINNER");
        assertThat(readProbe(loser.probeId())).isNull();
        return winner;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent worker interrupted", error);
        } catch (Exception error) {
            throw new IllegalStateException("Concurrent workers did not reach the barrier", error);
        }
    }

    private EngineDeployment fixture(String resource) throws Exception {
        var deployment = deploy(resource);
        deployments.add(deployment.deploymentId());
        return deployment;
    }

    private String probeId() {
        String id = "p16-" + UUID.randomUUID();
        probeIds.add(id);
        return id;
    }

    private record Attempt(String probeId, Number backendPid, ErrorCode error) { }
    private static final class DeliberateFailure extends RuntimeException { }
}
