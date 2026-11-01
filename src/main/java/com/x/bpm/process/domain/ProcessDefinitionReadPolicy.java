package com.x.bpm.process.domain;

import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;

/** Policy Java thuần; xác định phạm vi trước khi query, không đọc DB/HTTP/engine. */
public class ProcessDefinitionReadPolicy {

    public ProcessCatalogScope resolveScope(ActorContext actor) {
        if (actor == null || !actor.active()) {
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED,
                    "Danh tính không hợp lệ hoặc tài khoản đã ngừng hoạt động.");
        }
        // Giá trị permission hiện có của bài thực hành; chuẩn hóa RBAC tại P2.3.
        // Đây là quyền trong ActorContext, không phải role/header do client tự khai.
        if (actor.hasPermission("Admin")) {
            return ProcessCatalogScope.ALL;
        }
        if (actor.hasPermission("SubAdmin")) {
            return ProcessCatalogScope.STARTABLE_FOR_ACTOR;
        }
        throw new ApplicationException(ErrorCode.FORBIDDEN,
                "Bạn không có quyền xem danh mục quy trình.");
    }
}
