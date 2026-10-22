package com.trustvault.trustvault.repository;

import com.trustvault.trustvault.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository
        extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findAllByDocument_IdOrderByVersionNumberDesc(
            UUID documentId);

    Optional<DocumentVersion> findByIdAndDocument_Id(
            UUID versionId, UUID documentId);

    int countByDocument_Id(UUID documentId);
}
