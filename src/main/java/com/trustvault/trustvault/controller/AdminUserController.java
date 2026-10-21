
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.UserRole;
import com.trustvault.trustvault.repository.AppUserRepository;
import com.trustvault.trustvault.service.AuditService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AppUserRepository users;
    private final AuditService auditService;

    public AdminUserController(
            AppUserRepository users,
            AuditService auditService) {
        this.users = users;
        this.auditService = auditService;
    }

    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public RoleResponse changeRole(
            @PathVariable UUID userId,
            @RequestBody RoleRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        AppUser actor = users.findByEmailIgnoreCase(authentication.getName())
                .filter(AppUser::isEnabled)
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "An enabled administrator is required."));

        if (request == null
                || request.role() == null
                || request.role().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A role is required.");
        }

        final UserRole newRole;

        try {
            newRole = UserRole.valueOf(
                    request.role().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid role.");
        }

        AppUser target = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found."));

        UserRole previousRole = target.getRole();

        if (previousRole == newRole) {
            return new RoleResponse(
                    target.getId(),
                    target.getEmail(),
                    target.getRole().name());
        }

        if (target.isEnabled()
                && previousRole == UserRole.ADMIN
                && newRole != UserRole.ADMIN
                && users.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The last enabled administrator cannot be demoted.");
        }

        target.changeRole(newRole);
        users.saveAndFlush(target);

        auditService.record(
                actor.getId(),
                actor.getEmail(),
                "USER_ROLE_CHANGE",
                "SUCCESS",
                null,
                null,
                target.getId(),
                target.getEmail(),
                "ROLE_CHANGED_FROM_" + previousRole.name()
                        + "_TO_" + newRole.name(),
                httpRequest);

        return new RoleResponse(
                target.getId(),
                target.getEmail(),
                target.getRole().name());
    }

    public record RoleRequest(String role) {}

    public record RoleResponse(
            UUID id,
            String email,
            String role) {}
}
