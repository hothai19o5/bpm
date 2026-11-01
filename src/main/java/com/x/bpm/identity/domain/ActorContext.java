package com.x.bpm.identity.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Context do server tạo, không phải request DTO hay JPA entity.
 * userId là ID ứng dụng; permissions là quyền đã được identity xác thực.
 */
public record ActorContext(String userId, boolean active, Set<String> permissions) {
    public ActorContext {
        Objects.requireNonNull(userId, "userId must not be null");
        String trimmedUserId = userId.strip();
        if (trimmedUserId.isEmpty()) {
            throw new IllegalArgumentException("userId must not be blank");
        }

        Objects.requireNonNull(permissions, "permissions must not be null");

        // Gán lại giá trị đã chuẩn hóa và tạo defensive unmodifiable copy
        userId = trimmedUserId;
        permissions = Set.copyOf(permissions);

    }

    /**
     * Tiện ích kiểm tra nhanh quyền của actor.
     */
    public boolean hasPermission(String permission) {
        if (!active || permission == null) {
            return false;
        }
        return permissions.contains(permission);
    }

    /**
     * Factory tạo context ẩn danh hoặc system khi cần (nếu domain hỗ trợ).
     */
    public static ActorContext system() {
        return new ActorContext("SYSTEM", true, Set.of("*"));
    }
}
