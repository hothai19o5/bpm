package com.x.bpm.shared.error;

/** Mã ổn định theo mục 10.5 đặc tả; HTTP status được ánh xạ ở shared.api. */
public enum ErrorCode {
    INVALID_REQUEST,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    TASK_CONFLICT,
    REVISION_CONFLICT,
    VERSION_NOT_STARTABLE,
    IDEMPOTENCY_KEY_REUSED,
    VALIDATION_FAILED,
    INTERNAL_ERROR
}
