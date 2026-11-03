package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.LoginGrant;
import com.x.bpm.identity.domain.RefreshCredential;
import com.x.bpm.identity.domain.UserAccount;

/** Dịch model JPA sang model domain; không truyền entity qua boundary. */
final class IdentityPersistenceMapper {
    private IdentityPersistenceMapper() {
    }

    static UserAccount toDomain(UserEntity entity) {
        return new UserAccount(entity.getId(), entity.getUsername(), entity.getNormalizedUsername(),
                entity.getDisplayName(), entity.getPasswordHash(),
                AccountStatus.valueOf(entity.getStatus().name()), entity.isMustChangePassword(),
                entity.getSecurityVersion(), entity.getRevision());
    }

    static LoginGrant toDomain(LoginGrantEntity entity) {
        return new LoginGrant(entity.getId(), entity.getUser().getId(), entity.getSecurityVersion(),
                entity.getCreatedAt(), entity.getExpiresAt(), entity.getRevokedAt(),
                entity.getRevokeReason(), entity.getRevision());
    }

    static RefreshCredential toDomain(RefreshCredentialEntity entity) {
        return new RefreshCredential(entity.getId(), entity.getGrant().getId(), entity.getTokenHash(),
                entity.getCreatedAt(), entity.getExpiresAt(), entity.getConsumedAt(),
                entity.getRevision());
    }
}
