package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.UserAccount;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cổng persistence do application sở hữu; adapter chịu trách nhiệm map JPA.
 * Các phương thức ForUpdate/save phải được gọi trong transaction của use case.
 */
public interface AuthAccountPort {
    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findById(UUID userId);

    Optional<UserAccount> findByIdForUpdate(UUID userId);

    Set<String> findCurrentPermissions(UUID userId);

    UserAccount create(NewUserAccount account);

    UserAccount save(UserAccount account);
}
