package com.x.bpm.shared.api;

import com.x.bpm.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** Ánh xạ HTTP chỉ nằm ở tầng API, dùng chung cho MVC và security filter. */
public final class ApiErrorMapping {
    private ApiErrorMapping() {
    }

    public static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TASK_CONFLICT, REVISION_CONFLICT, VERSION_NOT_STARTABLE,
                    IDEMPOTENCY_KEY_REUSED -> HttpStatus.CONFLICT;
            case VALIDATION_FAILED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 422 -> ErrorCode.VALIDATION_FAILED;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.INVALID_REQUEST;
        };
    }

    public static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Yêu cầu không hợp lệ.";
            case 401 -> "Bearer token bị thiếu, không hợp lệ hoặc đã hết hạn.";
            case 403 -> "Bạn không có quyền thực hiện thao tác này.";
            case 404 -> "Không tìm thấy tài nguyên.";
            case 405 -> "Phương thức HTTP không được hỗ trợ.";
            case 406 -> "Định dạng phản hồi được yêu cầu không được hỗ trợ.";
            case 413 -> "Dữ liệu gửi lên vượt quá giới hạn cho phép.";
            case 415 -> "Kiểu nội dung gửi lên không được hỗ trợ.";
            case 422 -> "Dữ liệu không đáp ứng quy tắc kiểm tra.";
            default -> status.is5xxServerError()
                    ? "Có lỗi hệ thống. Vui lòng thử lại hoặc liên hệ quản trị viên."
                    : "Yêu cầu không thể được thực hiện.";
        };
    }
}
