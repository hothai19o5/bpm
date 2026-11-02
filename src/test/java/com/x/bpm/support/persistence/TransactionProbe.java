package com.x.bpm.support.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** P1.6 technical record; never included in the runtime JAR or dev schema. */
@Entity
@Table(name = "p16_transaction_probe")
public class TransactionProbe {
    @Id
    @Column(length = 100)
    private String id;
    @Column(nullable = false, length = 30)
    private String state;
    private String deploymentId;
    private String definitionId;
    private String instanceId;
    private String taskId;

    protected TransactionProbe() {
    }

    public TransactionProbe(String id, String state) {
        this.id = id;
        this.state = state;
    }

    public void link(String deploymentId, String definitionId, String instanceId, String taskId) {
        this.deploymentId = deploymentId;
        this.definitionId = definitionId;
        this.instanceId = instanceId;
        this.taskId = taskId;
    }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getDeploymentId() { return deploymentId; }
    public String getDefinitionId() { return definitionId; }
    public String getInstanceId() { return instanceId; }
    public String getTaskId() { return taskId; }
}
