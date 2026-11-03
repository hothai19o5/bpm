package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.LoginGrant;
import com.x.bpm.identity.domain.RefreshCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng lưu login grant/refresh credential. Mutation và truy vấn ForUpdate phải
 * nằm trong cùng transaction use case để giữ thứ tự khóa user → grant → credential.
 */
public interface AuthGrantPort {
    LoginGrant createGrant(NewLoginGrant grant);

    Optional<LoginGrant> findGrantById(UUID grantId);

    Optional<LoginGrant> findGrantByIdForUpdate(UUID grantId);

    LoginGrant saveGrant(LoginGrant grant);

    List<UUID> findUnrevokedGrantIdsByUserId(UUID userId);

    RefreshCredential createCredential(NewRefreshCredential credential);

    Optional<RefreshCredential> findCredentialByTokenHash(String tokenHash);

    Optional<RefreshCredential> findCredentialByIdForUpdate(UUID credentialId);

    RefreshCredential saveCredential(RefreshCredential credential);
}
