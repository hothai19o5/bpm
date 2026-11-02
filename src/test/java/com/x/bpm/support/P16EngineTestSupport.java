package com.x.bpm.support;

import com.x.bpm.process.application.engine.EngineDeployment;
import com.x.bpm.process.application.engine.EngineProcessInstance;
import com.x.bpm.process.application.engine.ProcessEnginePort;
import com.x.bpm.support.persistence.TransactionProbe;
import com.x.bpm.task.application.engine.EngineTask;
import com.x.bpm.task.application.engine.TaskEnginePort;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** No enclosing test transaction: assertions read committed state in fresh transactions. */
public abstract class P16EngineTestSupport {
    @Autowired protected ProcessEnginePort processes;
    @Autowired protected TaskEnginePort tasks;
    @Autowired protected RepositoryService repository;
    @Autowired protected RuntimeService runtime;
    @Autowired protected EntityManager entityManager;
    @Autowired protected PlatformTransactionManager transactionManager;

    protected <T> T inTransaction(Supplier<T> action) {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(30);
        return template.execute(status -> action.get());
    }

    protected EngineDeployment deploy(String resource) throws IOException {
        String xml = new ClassPathResource("fixtures/" + resource).getContentAsString(StandardCharsets.UTF_8);
        return processes.deploy("P1.6 " + UUID.randomUUID(), resource, xml);
    }

    protected EngineTask onlyTask(String instanceId) {
        var active = tasks.findActiveByInstance(instanceId);
        assertThat(active).hasSize(1);
        return active.getFirst();
    }

    protected EngineProcessInstance startDirect(EngineDeployment deployment, String key) {
        return processes.start(deployment.definitions().getFirst().definitionId(), key,
                Map.of("reviewerId", "reviewer-1"));
    }

    protected void insertProbe(String id, String state) {
        entityManager.persist(new TransactionProbe(id, state));
        // Send the SQL now: a later rollback must undo a real database write.
        entityManager.flush();
    }

    protected TransactionProbe readProbe(String id) {
        return inTransaction(() -> entityManager.find(TransactionProbe.class, id));
    }

    protected void assertFinished(String instanceId, String taskId, String endActivity) {
        assertThat(processes.findRunningInstance(instanceId)).isEmpty();
        assertThat(tasks.findActiveByInstance(instanceId)).isEmpty();
        var history = processes.findHistory(instanceId).orElseThrow();
        assertThat(history.endedAt()).isNotNull();
        assertThat(history.endActivityId()).isEqualTo(endActivity);
        assertThat(history.tasks()).singleElement().satisfies(task -> {
            assertThat(task.taskId()).isEqualTo(taskId);
            assertThat(task.assignee()).isEqualTo("reviewer-1");
            assertThat(task.completedBy()).isEqualTo("reviewer-1");
            assertThat(task.endedAt()).isNotNull();
        });
    }
}
