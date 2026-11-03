package com.x.bpm.identity.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Thu hồi grant sau khi caller đã khóa account, giữ thứ tự khóa user → grant. */
final class GrantRevocationSupport {
    private GrantRevocationSupport() {
    }

    static void revokeAll(AuthGrantPort grants, UUID userId, Instant revokedAt, String reason) {
        Objects.requireNonNull(grants, "grants");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(revokedAt, "revokedAt");
        for (var grantId : grants.findUnrevokedGrantIdsByUserId(userId)) {
            grants.findGrantByIdForUpdate(grantId)
                    .filter(grant -> grant.userId().equals(userId) && grant.revokedAt() == null)
                    .ifPresent(grant -> grants.saveGrant(grant.revoke(revokedAt, reason)));
        }
    }
}
