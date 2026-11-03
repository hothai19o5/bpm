package com.x.bpm.identity.application;

import java.util.Set;
import java.util.UUID;

/** Login use case chỉ gọi cổng này sau khi kiểm tra credentials/ACTIVE/quyền từ DB. */
public interface AccessTokenIssuer {
    IssuedAccessToken issue(UUID userId, UUID loginGrantId, long securityVersion,
            Set<String> permissions);
}
