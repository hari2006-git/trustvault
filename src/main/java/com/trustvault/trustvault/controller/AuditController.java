
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.UserRole;
import com.trustvault.trustvault.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    private final JdbcTemplate jdbcTemplate;
    private final AppUserRepository appUserRepository;

    public AuditController(
            JdbcTemplate jdbcTemplate,
            AppUserRepository appUserRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping
    public AuditPage getAuditEvents(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String search) {

        requireAdmin(authentication);

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Page cannot be negative.");
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100.");
        }

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();

        if (action != null && !action.isBlank()) {
            where.append(" AND action = ?");
            parameters.add(action.trim());
        }

        if (outcome != null && !outcome.isBlank()) {
            where.append(" AND outcome = ?");
            parameters.add(outcome.trim().toUpperCase());
        }

        if (search != null && !search.isBlank()) {
            where.append("""
                    AND (
                        COALESCE(actor_email, '') ILIKE ?
                        OR COALESCE(document_name, '') ILIKE ?
                        OR COALESCE(target_email, '') ILIKE ?
                        OR COALESCE(reason_code, '') ILIKE ?
                        OR COALESCE(details::text, '') ILIKE ?
                    )
                    """);

            String pattern = "%" + search.trim() + "%";
            for (int i = 0; i < 5; i++) {
                parameters.add(pattern);
            }
        }

        String whereSql = where.toString();

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events" + whereSql,
                Long.class,
                parameters.toArray());

        long offset = (long) page * size;

        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add(offset);

        String sql = """
                SELECT event_no, event_id, actor_user_id, actor_email,
                       action, outcome, document_id, document_name,
                       target_user_id, target_email, event_time,
                       remote_ip, user_agent, reason_code, details,
                       previous_hash, event_hash
                FROM audit_events
                """ + whereSql + """
                 ORDER BY event_no DESC
                 LIMIT ? OFFSET ?
                """;

        List<AuditEventView> events = jdbcTemplate.query(
                sql, AUDIT_EVENT_MAPPER, pageParameters.toArray());

        return new AuditPage(
                events,
                page,
                size,
                total == null ? 0 : total);
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required.");
        }

        AppUser actor = appUserRepository
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

    private static final RowMapper<AuditEventView> AUDIT_EVENT_MAPPER =
            AuditController::mapAuditEvent;

    private static AuditEventView mapAuditEvent(
            ResultSet rs, int rowNumber) throws SQLException {

        Timestamp timestamp = rs.getTimestamp("event_time");
        Instant eventTime = timestamp == null
                ? null : timestamp.toInstant();

        return new AuditEventView(
                rs.getLong("event_no"),
                rs.getObject("event_id", UUID.class),
                rs.getObject("actor_user_id", UUID.class),
                rs.getString("actor_email"),
                rs.getString("action"),
                rs.getString("outcome"),
                rs.getObject("document_id", UUID.class),
                rs.getString("document_name"),
                rs.getObject("target_user_id", UUID.class),
                rs.getString("target_email"),
                eventTime,
                rs.getString("remote_ip"),
                rs.getString("user_agent"),
                rs.getString("reason_code"),
                rs.getString("details"),
                rs.getString("previous_hash"),
                rs.getString("event_hash"));
    }

    public record AuditEventView(
            long eventNo,
            UUID eventId,
            UUID actorUserId,
            String actorEmail,
            String action,
            String outcome,
            UUID documentId,
            String documentName,
            UUID targetUserId,
            String targetEmail,
            Instant eventTime,
            String remoteIp,
            String userAgent,
            String reasonCode,
            String details,
            String previousHash,
            String eventHash) {
    }

    public record AuditPage(
            List<AuditEventView> events,
            int page,
            int size,
            long totalElements) {
    }
}
