
package com.trustvault.trustvault.service;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.repository.AppUserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser register(
            String email,
            String rawPassword,
            String displayName) {

        String normalizedEmail = normalizeEmail(email);
        String validatedName = validateDisplayName(displayName);
        validatePassword(rawPassword);

        if (appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists.");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);

        AppUser newUser = new AppUser(
                UUID.randomUUID(),
                normalizedEmail,
                passwordHash,
                validatedName
        );

        try {
            return appUserRepository.save(newUser);
        } catch (DataIntegrityViolationException exception) {
            // The database's unique index also protects against
            // simultaneous registration attempts for the same email.
            throw new IllegalArgumentException(
                    "An account with this email already exists.",
                    exception);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException(
                    "Email address is required.");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);

        if (normalized.length() > 320
                || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                    "Enter a valid email address.");
        }

        return normalized;
    }

    private String validateDisplayName(String displayName) {
        if (displayName == null) {
            throw new IllegalArgumentException(
                    "Display name is required.");
        }

        String normalized = displayName.trim();

        if (normalized.isEmpty()
                || normalized.length() > 100
                || normalized.codePoints()
                        .anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Display name must contain 1 to 100 valid characters.");
        }

        return normalized;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null
                || rawPassword.codePointCount(0, rawPassword.length()) < 12) {
            throw new IllegalArgumentException(
                    "Password must contain at least 12 characters.");
        }

        // BCrypt processes at most 72 UTF-8 bytes. Reject longer
        // passwords rather than silently truncating their effective input.
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Password must not exceed 72 UTF-8 bytes.");
        }
    }
}
