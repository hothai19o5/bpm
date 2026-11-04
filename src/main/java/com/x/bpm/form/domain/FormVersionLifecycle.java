package com.x.bpm.form.domain;

import com.x.bpm.shared.application.RevisionGuard;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;

/** Quy tắc lifecycle không phụ thuộc JPA/HTTP. */
public final class FormVersionLifecycle {
    private final FormSchemaContract contract = new FormSchemaContract();

    public void validate(FormVersion version, long expectedRevision) {
        RevisionGuard.requireMatch(expectedRevision, version.revision());
        contract.validate(version.schema(), version.uiSchema());
    }

    public void requireDraft(FormVersion version) {
        if (version.status() != FormVersion.Status.DRAFT)
            throw new ApplicationException(ErrorCode.REVISION_CONFLICT, "Chỉ được công bố phiên bản biểu mẫu nháp.");
    }

    public int nextVersion(int maximum) {
        if (maximum == Integer.MAX_VALUE)
            throw new ApplicationException(ErrorCode.REVISION_CONFLICT, "Đã đạt giới hạn số phiên bản biểu mẫu.");
        return maximum + 1;
    }
}
