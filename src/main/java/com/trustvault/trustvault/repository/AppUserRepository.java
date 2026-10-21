
package com.trustvault.trustvault.repository;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findAllByRole(UserRole role);

    long countByRoleAndEnabledTrue(UserRole role);
}
