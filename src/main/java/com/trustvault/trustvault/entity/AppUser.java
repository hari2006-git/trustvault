
package com.trustvault.trustvault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(
        name = "created_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    private Instant createdAt;

    protected AppUser() {
        // Required by JPA.
    }

    public AppUser(
            UUID id,
            String email,
            String passwordHash,
            String displayName) {

        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.enabled = true;

        // Public registration must never assign ADMIN.
        // New accounts receive OWNER by default.
        this.role = UserRole.OWNER;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Changes this user's application role.
     *
     * This method does not perform authorization itself.
     * Only trusted, server-side code must call it after
     * verifying that the caller is permitted to change roles.
     */
    public void changeRole(UserRole newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException(
                    "A valid role is required.");
        }

        this.role = newRole;
    }
}
