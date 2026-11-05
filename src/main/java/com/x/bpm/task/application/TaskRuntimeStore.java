package com.x.bpm.task.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TaskRuntimeStore {
    List<BusinessTask> assigned(String userId);
    List<BusinessTask> candidates(Set<String> groups);
    BusinessTask find(String taskId);
    void lockRecord(UUID recordId);
    TaskFormContext form(BusinessTask task);
}
