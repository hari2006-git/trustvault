
package com.trustvault.trustvault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_permissions")
public class DocumentPermission {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 10)
    private DocumentAccessLevel permission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "granted_by", nullable = false)
    private AppUser grantedBy;

    @Column(
        name = "granted_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private AppUser revokedBy;

    protected DocumentPermission() {
        // Required by JPA.
    }

    public DocumentPermission(
            UUID id,
            Document document,
            AppUser user,
            DocumentAccessLevel permission,
            AppUser grantedBy) {

        this.id = id;
        this.document = document;
        this.user = user;
        this.permission = permission;
        this.grantedBy = grantedBy;
    }

    public UUID getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public AppUser getUser() {
        return user;
    }

    public DocumentAccessLevel getPermission() {
        return permission;
    }

    public AppUser getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public AppUser getRevokedBy() {
        return revokedBy;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke(AppUser actor) {
        if (!isActive()) {
            throw new IllegalStateException(
                    "This permission has already been revoked.");
        }

        if (actor == null) {
            throw new IllegalArgumentException(
                    "The revoking user is required.");
        }

        this.revokedAt = Instant.now();
        this.revokedBy = actor;
    }
}
