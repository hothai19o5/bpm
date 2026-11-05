package com.x.bpm.task.assignment.application;

import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;

public final class AssignmentExceptionsUseCase {
    private final CurrentActorProvider actors;
    private final RuntimeAssignmentStore store;
    public AssignmentExceptionsUseCase(CurrentActorProvider actors, RuntimeAssignmentStore store) {
        this.actors = actors; this.store = store;
    }
    public PageResult<AssignmentExceptionView> list(PageQuery page) {
        if (!actors.requireCurrentActor().hasPermission("task.reassign"))
            throw new ApplicationException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem ngoại lệ phân công.");
        return store.exceptions(page);
    }
}
