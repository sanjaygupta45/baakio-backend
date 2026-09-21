package com.baakio.scan.persistence;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ScanRepository {
    private static final RowMapper<ScanJobRecord> JOB_ROW = (rs, row) -> new ScanJobRecord(
            rs.getLong("id"), rs.getString("business_id"), rs.getLong("customer_id"),
            rs.getString("object_key"), rs.getString("sha256"), rs.getString("mime_type"),
            rs.getString("status"), rs.getInt("attempt_count"), rs.getString("error_code"),
            rs.getString("upload_idempotency_key"), rs.getBoolean("keep_attachment"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("locked_at")),
            rs.getString("claim_token"), instant(rs.getTimestamp("image_deleted_at")));

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ScanRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<Long> findUploadByKey(String businessId, String key) {
        List<Long> ids = jdbc.query("select id from scan_job where business_id=? and upload_idempotency_key=?",
                (rs, row) -> rs.getLong(1), businessId, key);
        return ids.stream().findFirst();
    }

    @Transactional
    public ScanJobRecord createQueued(String businessId, long customerId, String objectKey, String sha256,
                                     String mimeType, String uploadKey, boolean keepAttachment) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into scan_job(business_id, customer_id, object_key, sha256, mime_type, status,
                      upload_idempotency_key, keep_attachment)
                    values (?, ?, ?, ?, ?, 'QUEUED', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, businessId);
            statement.setLong(2, customerId);
            statement.setString(3, objectKey);
            statement.setString(4, sha256);
            statement.setString(5, mimeType);
            statement.setString(6, uploadKey);
            statement.setBoolean(7, keepAttachment);
            return statement;
        }, keys);
        Number generated = keys.getKey();
        if (generated == null) throw new IllegalStateException("Scan job ID was not generated.");
        long id = generated.longValue();
        jdbc.update("""
                insert into outbox_event(business_id, aggregate_id, event_type, payload_json)
                values (?, ?, 'scan.requested', ?)
                """, businessId, id, "{\"scanId\":" + id + "}");
        return findById(id).orElseThrow();
    }

    public Optional<ScanJobRecord> findById(long id) {
        return jdbc.query("select * from scan_job where id=?", JOB_ROW, id).stream().findFirst();
    }

    public Optional<ScanJobRecord> findForBusiness(String businessId, long id) {
        return jdbc.query("select * from scan_job where business_id=? and id=?", JOB_ROW, businessId, id).stream().findFirst();
    }

    @Transactional
    public Optional<ScanClaim> claim(long id) {
        ScanJobRecord job = jdbc.query("select * from scan_job where id=? for update", JOB_ROW, id)
                .stream().findFirst().orElse(null);
        if (job == null || "REVIEW_REQUIRED".equals(job.status()) || "POSTED".equals(job.status())
                || "FAILED".equals(job.status()) || "CANCELLED".equals(job.status())) return Optional.empty();
        boolean stale = "PROCESSING".equals(job.status()) && job.lockedAt() != null
                && job.lockedAt().isBefore(Instant.now().minusSeconds(300));
        if (!"QUEUED".equals(job.status()) && !stale) return Optional.empty();
        if (job.attemptCount() >= 3) {
            jdbc.update("update scan_job set status='FAILED', error_code='RETRIES_EXHAUSTED', locked_at=null, claim_token=null where id=?", id);
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString();
        int attempt = job.attemptCount() + 1;
        jdbc.update("""
                update scan_job set status='PROCESSING', attempt_count=?, locked_at=UTC_TIMESTAMP(6), claim_token=?, error_code=null
                 where id=?
                """, attempt, token, id);
        jdbc.update("insert into scan_attempt(scan_job_id, attempt_no, status) values (?, ?, 'PROCESSING')", id, attempt);
        return findById(id).map(claimed -> new ScanClaim(claimed, token, attempt));
    }

    @Transactional
    public boolean saveDraft(ScanClaim claim, String extractedJson, ScanDraft draft,
                             String flagsJson, BigDecimal writtenTotal, BigDecimal calculatedTotal) {
        int updated = jdbc.update("""
                update scan_job set status='REVIEW_REQUIRED', locked_at=null, claim_token=null, error_code=null
                 where id=? and status='PROCESSING' and claim_token=?
                """, claim.job().id(), claim.token());
        if (updated == 0) return false;
        jdbc.update("""
                insert into scan_draft(scan_job_id, extracted_json, edited_json, written_total, calculated_total, flags_json, version)
                values (?, ?, ?, ?, ?, ?, 1)
                """, claim.job().id(), extractedJson, json(draft), writtenTotal, calculatedTotal, flagsJson);
        finishAttempt(claim, "REVIEW_REQUIRED", null);
        return true;
    }

    @Transactional
    public void markFailed(ScanClaim claim, String errorCode) {
        jdbc.update("""
                update scan_job set status='FAILED', error_code=?, locked_at=null, claim_token=null
                 where id=? and status='PROCESSING' and claim_token=?
                """, errorCode, claim.job().id(), claim.token());
        finishAttempt(claim, "FAILED", errorCode);
    }

    @Transactional
    public int recoverStaleClaims() {
        List<ScanJobRecord> stale = jdbc.query("""
                select * from scan_job where status='PROCESSING' and locked_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 5 MINUTE)
                order by locked_at limit 50 for update skip locked
                """, JOB_ROW);
        for (ScanJobRecord job : stale) {
            if (job.attemptCount() >= 3) {
                jdbc.update("update scan_job set status='FAILED', error_code='RETRIES_EXHAUSTED', locked_at=null, claim_token=null where id=?", job.id());
            } else {
                jdbc.update("update scan_job set status='QUEUED', error_code=null, locked_at=null, claim_token=null where id=?", job.id());
                jdbc.update("insert into outbox_event(business_id, aggregate_id, event_type, payload_json) values (?, ?, 'scan.requested', ?)",
                        job.businessId(), job.id(), "{\"scanId\":" + job.id() + "}");
            }
        }
        return stale.size();
    }

    public ScanDraftRecord findDraft(long scanId) {
        return jdbc.query("""
                select id, scan_job_id, extracted_json, edited_json, written_total, calculated_total,
                       flags_json, version, posted_purchase_id from scan_draft where scan_job_id=?
                """, (rs, row) -> new ScanDraftRecord(rs.getLong("id"), rs.getLong("scan_job_id"),
                rs.getString("extracted_json"), rs.getString("edited_json"), rs.getString("written_total"),
                rs.getString("calculated_total"), rs.getString("flags_json"), rs.getLong("version"),
                (Long) rs.getObject("posted_purchase_id")), scanId).stream().findFirst().orElse(null);
    }

    @Transactional
    public ScanJobRecord lockForBusiness(String businessId, long id) {
        return jdbc.query("select * from scan_job where business_id=? and id=? for update", JOB_ROW,
                businessId, id).stream().findFirst().orElse(null);
    }

    @Transactional
    public ScanDraftRecord lockDraft(long scanId) {
        return jdbc.query("""
                select id, scan_job_id, extracted_json, edited_json, written_total, calculated_total,
                       flags_json, version, posted_purchase_id from scan_draft where scan_job_id=? for update
                """, (rs, row) -> new ScanDraftRecord(rs.getLong("id"), rs.getLong("scan_job_id"),
                rs.getString("extracted_json"), rs.getString("edited_json"), rs.getString("written_total"),
                rs.getString("calculated_total"), rs.getString("flags_json"), rs.getLong("version"),
                (Long) rs.getObject("posted_purchase_id")), scanId).stream().findFirst().orElse(null);
    }

    @Transactional
    public void markPosted(long scanId, long oldVersion, long purchaseId, String editedJson, BigDecimal calculatedTotal) {
        int draftUpdated = jdbc.update("""
                update scan_draft set edited_json=?, calculated_total=?, version=version+1, posted_purchase_id=?
                 where scan_job_id=? and version=? and posted_purchase_id is null
                """, editedJson, calculatedTotal, purchaseId, scanId, oldVersion);
        if (draftUpdated != 1) throw new IllegalStateException("Scan draft changed during confirmation.");
        int jobUpdated = jdbc.update("update scan_job set status='POSTED', error_code=null where id=? and status='REVIEW_REQUIRED'", scanId);
        if (jobUpdated != 1) throw new IllegalStateException("Scan job changed during confirmation.");
    }

    public List<ScanJobRecord> findExpiredImages(int retentionDays) {
        return jdbc.query("""
                select * from scan_job where keep_attachment=false and image_deleted_at is null
                  and created_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ? DAY)
                order by created_at limit 100
                """, JOB_ROW, retentionDays);
    }

    @Transactional
    public void markImageDeleted(long scanId) {
        jdbc.update("update scan_job set image_deleted_at=UTC_TIMESTAMP(6) where id=? and keep_attachment=false and image_deleted_at is null", scanId);
    }

    private void finishAttempt(ScanClaim claim, String status, String errorCode) {
        jdbc.update("""
                update scan_attempt set status=?, error_code=?, completed_at=UTC_TIMESTAMP(6)
                 where scan_job_id=? and attempt_no=?
                """, status, errorCode, claim.job().id(), claim.attemptNo());
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Scan draft could not be saved.", exception);
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
