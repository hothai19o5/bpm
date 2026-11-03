package com.x.bpm.identity.adapter.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoginGrantJpaRepository extends JpaRepository<LoginGrantEntity, UUID> {
    @Query("select g.id from LoginGrantEntity g where g.user.id = :userId and g.revokedAt is null order by g.id")
    List<UUID> findUnrevokedIdsByUserId(@Param("userId") UUID userId);

    @Query("select g from LoginGrantEntity g join fetch g.user where g.id = :grantId")
    Optional<LoginGrantEntity> findDetailedById(@Param("grantId") UUID grantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from LoginGrantEntity g join fetch g.user where g.id = :grantId")
    Optional<LoginGrantEntity> findDetailedByIdForUpdate(@Param("grantId") UUID grantId);
}
