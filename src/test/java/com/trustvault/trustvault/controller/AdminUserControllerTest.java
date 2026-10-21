package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.config.SecurityConfig;
import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.UserRole;
import com.trustvault.trustvault.repository.AppUserRepository;
import com.trustvault.trustvault.service.AuditService;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppUserRepository users;

    @MockBean
    private AuditService auditService;

    private AppUser createUser(String email, String name) {
        return new AppUser(
                UUID.randomUUID(),
                email,
                "test-password-hash",
                name);
    }

    private void authenticateAdminRepository() {
        AppUser admin = createUser("admin@example.com", "Test Admin");
        admin.changeRole(UserRole.ADMIN);

        when(users.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(admin));
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void nonAdminCannotChangeUserRole() throws Exception {
        mockMvc.perform(
                        patch("/api/admin/users/{userId}/role",
                                UUID.randomUUID())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"role":"ADMIN"}
                                        """))
                .andExpect(status().isForbidden());

        verify(users, never()).saveAndFlush(any(AppUser.class));
        verify(auditService, never()).record(
                any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCanChangeRoleAndChangeIsAudited() throws Exception {
        authenticateAdminRepository();

        AppUser target = createUser(
                "target@example.com", "Test Target");

        when(users.findById(target.getId()))
                .thenReturn(Optional.of(target));
        when(users.saveAndFlush(target)).thenReturn(target);

        mockMvc.perform(
                        patch("/api/admin/users/{userId}/role", target.getId())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"role":"VIEWER"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId().toString()))
                .andExpect(jsonPath("$.email").value("target@example.com"))
                .andExpect(jsonPath("$.role").value("VIEWER"));

        verify(users).saveAndFlush(target);

        verify(auditService).record(
                any(UUID.class),
                eq("admin@example.com"),
                eq("USER_ROLE_CHANGE"),
                eq("SUCCESS"),
                isNull(),
                isNull(),
                eq(target.getId()),
                eq("target@example.com"),
                eq("ROLE_CHANGED_FROM_OWNER_TO_VIEWER"),
                any(HttpServletRequest.class));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void invalidRoleIsRejected() throws Exception {
        authenticateAdminRepository();

        mockMvc.perform(
                        patch("/api/admin/users/{userId}/role",
                                UUID.randomUUID())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"role":"SUPERUSER"}
                                        """))
                .andExpect(status().isBadRequest());

        verify(users, never()).saveAndFlush(any(AppUser.class));
        verify(auditService, never()).record(
                any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void lastEnabledAdminCannotBeDemoted() throws Exception {
        authenticateAdminRepository();

        AppUser target = createUser(
                "last-admin@example.com", "Last Admin");
        target.changeRole(UserRole.ADMIN);

        when(users.findById(target.getId()))
                .thenReturn(Optional.of(target));
        when(users.countByRoleAndEnabledTrue(UserRole.ADMIN))
                .thenReturn(1L);

        mockMvc.perform(
                        patch("/api/admin/users/{userId}/role", target.getId())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"role":"VIEWER"}
                                        """))
                .andExpect(status().isConflict());

        verify(users, never()).saveAndFlush(any(AppUser.class));
        verify(auditService, never()).record(
                any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }
}
