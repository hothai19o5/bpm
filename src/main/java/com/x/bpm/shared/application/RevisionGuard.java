package com.x.bpm.shared.application;

import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;

/** So sánh revision là tiền kiểm tra; không thay optimistic locking tại database. */
public final class RevisionGuard {

    private RevisionGuard() {
        // Utility class, không tạo bean.
    }

    public static void requireMatch(long expectedRevision, long actualRevision) {
        // Persistence vẫn phải kiểm tra optimistic locking trong transaction.
        if (expectedRevision < 0) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,
                    "expectedRevision phải lớn hơn hoặc bằng 0.");
        }
        if (actualRevision < 0) {
            throw new IllegalStateException("Stored revision must not be negative");
        }
        if (expectedRevision != actualRevision) {
            throw new ApplicationException(
                    ErrorCode.REVISION_CONFLICT,
                    String.format("Revision conflict: expected revision %d but found %d", expectedRevision, actualRevision)
            );
        }
        // Trùng nhau: kết thúc bình thường (no-op), không tự tăng revision ở đây
    }
}
