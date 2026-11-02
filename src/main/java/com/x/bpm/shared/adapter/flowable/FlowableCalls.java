package com.x.bpm.shared.adapter.flowable;

import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.function.Supplier;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;

/** Prevent engine exception messages from becoming the public application contract. */
public final class FlowableCalls {
    private FlowableCalls() {
    }

    public static <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (FlowableObjectNotFoundException exception) {
            throw translate(ErrorCode.NOT_FOUND, "Engine resource was not found", exception);
        } catch (FlowableTaskAlreadyClaimedException | FlowableOptimisticLockingException exception) {
            throw translate(ErrorCode.TASK_CONFLICT, "Engine state has changed; reload before retrying", exception);
        } catch (FlowableException exception) {
            throw translate(ErrorCode.INTERNAL_ERROR, "Process engine operation failed", exception);
        }
    }

    private static ApplicationException translate(ErrorCode code, String message, RuntimeException cause) {
        var exception = new ApplicationException(code, message);
        exception.initCause(cause);
        return exception;
    }
}
