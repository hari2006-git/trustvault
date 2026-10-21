
package com.trustvault.trustvault.repository;

import com.trustvault.trustvault.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository
        extends JpaRepository<Document, UUID> {

    List<Document> findAllByOwner_IdOrderByCreatedAtDesc(
            UUID ownerId);

    Optional<Document> findByIdAndOwner_Id(
            UUID documentId,
            UUID ownerId);
}
