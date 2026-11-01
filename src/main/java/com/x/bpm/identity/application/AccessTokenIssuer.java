package com.x.bpm.identity.application;

import java.util.Set;

/** Login use case chỉ gọi cổng này sau khi kiểm tra credentials/ACTIVE/quyền từ DB. */
public interface AccessTokenIssuer {
    IssuedAccessToken issue(String userId, Set<String> permissions);
}
