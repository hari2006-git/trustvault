package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.UserRole;
import com.trustvault.trustvault.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditIntegrityController {

    private final JdbcTemplate jdbc;
    private final AppUserRepository users;

    public AuditIntegrityController(
            JdbcTemplate jdbc,
            AppUserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @GetMapping("/integrity")
    public IntegrityReport verify(Authentication authentication) {
        requireAdmin(authentication);

        List<ChainState> states = jdbc.query(
                """
                SELECT last_event_no, RTRIM(last_event_hash)
                FROM audit_chain_state
                WHERE id = 1
                """,
                (rs, row) -> new ChainState(
                        rs.getLong(1), rs.getString(2)));

        if (states.size() != 1) {
            return new IntegrityReport(
                    false, 0, null,
                    "Audit chain state is missing or invalid.");
        }

        ChainState state = states.get(0);

        List<ChainCheck> events = jdbc.query(
                """
                SELECT event_no, RTRIM(previous_hash), RTRIM(event_hash)
                FROM audit_events
                ORDER BY event_no ASC
                """,
                (rs, row) -> new ChainCheck(
                        rs.getLong(1),
                        rs.getString(2),
                        rs.getString(3)));

        long expectedNumber = 1;
        String expectedPreviousHash = "0".repeat(64);

        for (ChainCheck event : events) {
            if (event.eventNo() != expectedNumber) {
                return new IntegrityReport(
                        false, events.size(), event.eventNo(),
                        "Audit event numbering has a gap or inconsistency.");
            }

            if (!expectedPreviousHash.equals(event.previousHash())) {
                return new IntegrityReport(
                        false, events.size(), event.eventNo(),
                        "Audit hash-link continuity check failed.");
            }

            expectedPreviousHash = event.eventHash();
            expectedNumber++;
        }

        long actualLastNumber = events.isEmpty()
                ? 0 : events.get(events.size() - 1).eventNo();

        if (state.lastEventNo() != actualLastNumber
                || !state.lastEventHash().equals(expectedPreviousHash)) {
            return new IntegrityReport(
                    false, events.size(), actualLastNumber,
                    "Stored audit chain state does not match the event history.");
        }

        return new IntegrityReport(
                true, events.size(), null,
                "Event numbering, hash links, and chain state are consistent. " +
                "Event-content hashes have not been independently recomputed.");
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required.");
        }

        AppUser actor = users
                .findByEmailIgnoreCase(authentication.getName())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "The authenticated account is unavailable."));

        if (actor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator access is required.");
        }
    }

    private record ChainState(long lastEventNo, String lastEventHash) {}
    private record ChainCheck(
            long eventNo, String previousHash, String eventHash) {}

    public record IntegrityReport(
            boolean consistent,
            long eventsChecked,
            Long firstInconsistentEvent,
            String message) {}
}
