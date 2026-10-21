
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.DocumentAccessLevel;
import com.trustvault.trustvault.repository.AppUserRepository;
import com.trustvault.trustvault.service.DocumentPermissionService;
import com.trustvault.trustvault.service.DocumentPermissionService.PermissionView;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents/{documentId}/permissions")
public class DocumentPermissionController {

    private final DocumentPermissionService permissionService;
    private final AppUserRepository appUserRepository;

    public DocumentPermissionController(
            DocumentPermissionService permissionService,
            AppUserRepository appUserRepository) {

        this.permissionService = permissionService;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping
    public List<PermissionView> listPermissions(
            @PathVariable UUID documentId,
            Authentication authentication) {

        AppUser actor = currentUser(authentication);

        return permissionService.listPermissions(
                documentId,
                actor.getId());
    }

    @PostMapping
    public ResponseEntity<PermissionView> grantPermission(
            @PathVariable UUID documentId,
            @RequestBody GrantRequest request,
            Authentication authentication) {

        AppUser actor = currentUser(authentication);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A permission request is required.");
        }

        try {
            PermissionView created = permissionService.grantPermission(
                    documentId,
                    actor.getId(),
                    request.email(),
                    request.permission()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(created);

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage());
        }
    }

    @DeleteMapping("/{permissionId}")
    public ResponseEntity<Void> revokePermission(
            @PathVariable UUID documentId,
            @PathVariable UUID permissionId,
            Authentication authentication) {

        AppUser actor = currentUser(authentication);

        permissionService.revokePermission(
                documentId,
                permissionId,
                actor.getId());

        return ResponseEntity.noContent().build();
    }

    private AppUser currentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required.");
        }

        return appUserRepository
                .findByEmailIgnoreCase(authentication.getName())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "The authenticated account is unavailable."));
    }

    public record GrantRequest(
            String email,
            DocumentAccessLevel permission) {
    }
}
