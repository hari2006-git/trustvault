
package com.trustvault.trustvault.service;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;

    public AuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID actorId,
            String actorEmail,
            String action,
            String outcome,
            UUID documentId,
            String documentName,
            UUID targetUserId,
            String targetEmail,
            String reasonCode,
            HttpServletRequest request) {

        String normalizedActor = limit(
                actorEmail == null || actorEmail.isBlank()
                        ? "anonymous"
                        : actorEmail,
                320);

        requireText(action, "action", 50);
        requireText(outcome, "outcome", 12);

        if (!List.of("SUCCESS", "DENIED", "FAILURE").contains(outcome)) {
            throw new IllegalArgumentException(
                    "Unsupported audit outcome.");
        }

        String safeAction = action.trim().toUpperCase();

        Instant eventTime = Instant.now();
        UUID eventId = UUID.randomUUID();

        String remoteIp = request == null
                ? null
                : limit(request.getRemoteAddr(), 64);

        String userAgent = request == null
                ? null
                : limit(request.getHeader("User-Agent"), 512);

        String safeDocumentName = limit(documentName, 255);
        String safeTargetEmail = limit(targetEmail, 320);
        String safeReason = limit(reasonCode, 100);

        // Locking this row serializes event numbering and hash-chain writes.
        List<ChainState> states = jdbcTemplate.query(
                """
                SELECT last_event_no, RTRIM(last_event_hash)
                FROM audit_chain_state
                WHERE id = 1
                FOR UPDATE
                """,
                (rs, rowNum) -> new ChainState(
                        rs.getLong(1),
                        rs.getString(2)));

        if (states.size() != 1) {
            throw new IllegalStateException(
                    "The audit chain state is missing or invalid.");
        }

        ChainState state = states.get(0);
        long eventNo = Math.addExact(state.lastEventNo(), 1);
        String previousHash = state.lastEventHash();

        // Details is intentionally fixed and included in the hash input.
        String eventHash = calculateHash(
                previousHash,
                Long.toString(eventNo),
                eventId.toString(),
                value(actorId),
                normalizedActor,
                safeAction,
                outcome,
                value(documentId),
                value(safeDocumentName),
                value(targetUserId),
                value(safeTargetEmail),
                eventTime.toString(),
                value(remoteIp),
                value(userAgent),
                value(safeReason),
                "{}");

        jdbcTemplate.update(
                """
                INSERT INTO audit_events (
                    event_no,
                    event_id,
                    actor_user_id,
                    actor_email,
                    action,
                    outcome,
                    document_id,
                    document_name,
                    target_user_id,
                    target_email,
                    event_time,
                    remote_ip,
                    user_agent,
                    reason_code,
                    details,
                    previous_hash,
                    event_hash
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, CAST(? AS jsonb), ?, ?
                )
                """,
                eventNo,
                eventId,
                actorId,
                normalizedActor,
                safeAction,
                outcome,
                documentId,
                safeDocumentName,
                targetUserId,
                safeTargetEmail,
                Timestamp.from(eventTime),
                remoteIp,
                userAgent,
                safeReason,
                "{}",
                previousHash,
                eventHash);

        jdbcTemplate.update(
                """
                UPDATE audit_chain_state
                SET last_event_no = ?,
                    last_event_hash = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                """,
                eventNo,
                eventHash);
    }

    private String calculateHash(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            for (String field : fields) {
                byte[] bytes = value(field)
                        .getBytes(StandardCharsets.UTF_8);

                // Length-prefix each field so delimiters inside values
                // cannot create ambiguous hash inputs.
                digest.update(
                        ByteBuffer.allocate(Integer.BYTES)
                                .putInt(bytes.length)
                                .array());

                digest.update(bytes);
            }

            return HexFormat.of().formatHex(digest.digest());

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available.", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.length() <= maxLength
                ? trimmed
                : trimmed.substring(0, maxLength);
    }

    private static void requireText(
            String value,
            String field,
            int maxLength) {

        if (value == null
                || value.isBlank()
                || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Invalid audit " + field + ".");
        }
    }

    private record ChainState(
            long lastEventNo,
            String lastEventHash) {
    }
}
