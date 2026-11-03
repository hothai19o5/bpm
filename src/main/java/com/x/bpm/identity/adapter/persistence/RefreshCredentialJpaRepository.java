package com.x.bpm.identity.adapter.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshCredentialJpaRepository extends JpaRepository<RefreshCredentialEntity, UUID> {
    @Query("""
            select c from RefreshCredentialEntity c
            join fetch c.grant g
            where c.tokenHash = :tokenHash
            """)
    Optional<RefreshCredentialEntity> findDetailedByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from RefreshCredentialEntity c
            join fetch c.grant g
            where c.id = :credentialId
            """)
    Optional<RefreshCredentialEntity> findDetailedByIdForUpdate(
            @Param("credentialId") UUID credentialId);
}
