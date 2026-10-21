
package com.trustvault.trustvault.repository;

import com.trustvault.trustvault.entity.DocumentPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentPermissionRepository
        extends JpaRepository<DocumentPermission, UUID> {

    Optional<DocumentPermission>
    findByDocument_IdAndUser_IdAndRevokedAtIsNull(
            UUID documentId,
            UUID userId);

    List<DocumentPermission>
    findAllByDocument_IdAndRevokedAtIsNullOrderByGrantedAtDesc(
            UUID documentId);

    List<DocumentPermission>
    findAllByUser_IdAndRevokedAtIsNull(UUID userId);
}
