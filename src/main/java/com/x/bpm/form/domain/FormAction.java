package com.x.bpm.form.domain;

/** Action được use case hồ sơ/task xác thực trước khi gọi form service. */
public enum FormAction {
    SAVE_DRAFT, SUBMIT, RESUBMIT, APPROVE, COMPLETE, REJECT, REQUEST_CHANGES;

    public boolean requiresCompleteFields() {
        return this == SUBMIT || this == RESUBMIT || this == APPROVE || this == COMPLETE;
    }

    public boolean requiresReason() {
        return this == REJECT || this == REQUEST_CHANGES;
    }
}
