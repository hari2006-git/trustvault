
package com.trustvault.trustvault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(
        name = "created_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private AppUser deletedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    protected Document() {
        // Required by JPA.
    }

    public Document(
            UUID id,
            String originalName,
            String storageKey,
            String contentType,
            Long sizeBytes,
            String checksumSha256,
            AppUser owner) {

        this.id = id;
        this.originalName = originalName;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.owner = owner;
    }

    public UUID getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        if (category != null) {
            category = category.trim();

            if (category.isEmpty()) {
                category = null;
            }

            if (category != null && category.length() > 100) {
                throw new IllegalArgumentException(
                        "Category must not exceed 100 characters.");
            }
        }

        this.category = category;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public AppUser getDeletedBy() {
        return deletedBy;
    }

    public AppUser getOwner() {
        return owner;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted(AppUser user) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Deleting user is required.");
        }

        if (isDeleted()) {
            throw new IllegalStateException(
                    "Document has already been deleted.");
        }

        this.deletedAt = Instant.now();
        this.deletedBy = user;
    }
}
