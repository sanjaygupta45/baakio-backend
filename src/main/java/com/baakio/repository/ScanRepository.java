package com.baakio.repository;

import com.baakio.ai.ScanDraft;
import com.baakio.model.OutboxEvent;
import com.baakio.model.ScanAttempt;
import com.baakio.model.ScanJob;
import com.baakio.model.StoredScanDraft;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Coordinates the scan entities; Spring Data repositories own the persistence operations. */
@Repository
public class ScanRepository {
    private final ScanJobRepository jobs;
    private final ScanDraftRepository drafts;
    private final ScanAttemptRepository attempts;
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;

    public ScanRepository(ScanJobRepository jobs, ScanDraftRepository drafts,
                          ScanAttemptRepository attempts, OutboxEventRepository outbox,
                          ObjectMapper mapper) {
        this.jobs = jobs;
        this.drafts = drafts;
        this.attempts = attempts;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    public Optional<Long> findUploadByKey(String businessId, String key) {
        return jobs.findByBusinessIdAndUploadIdempotencyKey(businessId, key).map(ScanJob::getId);
    }

    @Transactional
    public ScanJobRecord createQueued(String businessId, long customerId, String objectKey, String sha256,
                                      String mimeType, String uploadKey, boolean keepAttachment) {
        ScanJob job = jobs.saveAndFlush(new ScanJob(businessId, customerId, objectKey,
                sha256, mimeType, uploadKey, keepAttachment));
        outbox.save(new OutboxEvent(businessId, job.getId(), "scan.requested",
                "{\"scanId\":" + job.getId() + "}"));
        return record(job);
    }

    public Optional<ScanJobRecord> findById(long id) {
        return jobs.findById(id).map(ScanRepository::record);
    }

    public Optional<ScanJobRecord> findForBusiness(String businessId, long id) {
        return jobs.findByBusinessIdAndId(businessId, id).map(ScanRepository::record);
    }

    @Transactional
    public Optional<ScanClaim> claim(long id) {
        ScanJob job = jobs.findLockedById(id).orElse(null);
        if (job == null || isTerminal(job.getStatus())) return Optional.empty();
        boolean stale = "PROCESSING".equals(job.getStatus()) && job.getLockedAt() != null
                && job.getLockedAt().isBefore(Instant.now().minusSeconds(300));
        if (!"QUEUED".equals(job.getStatus()) && !stale) return Optional.empty();
        if (job.getAttemptCount() >= 3) {
            job.setStatus("FAILED");
            job.setErrorCode("RETRIES_EXHAUSTED");
            job.setLockedAt(null);
            job.setClaimToken(null);
            return Optional.empty();
        }

        String token = UUID.randomUUID().toString();
        int attemptNo = job.getAttemptCount() + 1;
        job.setStatus("PROCESSING");
        job.setAttemptCount(attemptNo);
        job.setLockedAt(Instant.now());
        job.setClaimToken(token);
        job.setErrorCode(null);
        attempts.save(new ScanAttempt(id, attemptNo, "PROCESSING"));
        jobs.flush();
        return Optional.of(new ScanClaim(record(job), token, attemptNo));
    }

    @Transactional
    public boolean saveDraft(ScanClaim claim, String extractedJson, ScanDraft draft,
                             String flagsJson, BigDecimal writtenTotal, BigDecimal calculatedTotal) {
        ScanJob job = jobs.findLockedById(claim.job().id()).orElse(null);
        if (!ownsClaim(job, claim)) return false;
        job.setStatus("REVIEW_REQUIRED");
        job.setLockedAt(null);
        job.setClaimToken(null);
        job.setErrorCode(null);
        drafts.save(new StoredScanDraft(job.getId(), extractedJson, json(draft), writtenTotal,
                calculatedTotal, flagsJson));
        finishAttempt(claim, "REVIEW_REQUIRED", null);
        return true;
    }

    @Transactional
    public void markFailed(ScanClaim claim, String errorCode) {
        ScanJob job = jobs.findLockedById(claim.job().id()).orElse(null);
        if (!ownsClaim(job, claim)) return;
        job.setStatus("FAILED");
        job.setErrorCode(errorCode);
        job.setLockedAt(null);
        job.setClaimToken(null);
        finishAttempt(claim, "FAILED", errorCode);
    }

    @Transactional
    public int recoverStaleClaims() {
        List<ScanJob> staleJobs = jobs.lockStaleClaims(Instant.now().minusSeconds(300));
        for (ScanJob job : staleJobs) {
            if (job.getAttemptCount() >= 3) {
                job.setStatus("FAILED");
                job.setErrorCode("RETRIES_EXHAUSTED");
                job.setLockedAt(null);
                job.setClaimToken(null);
            } else {
                job.setStatus("QUEUED");
                job.setErrorCode(null);
                job.setLockedAt(null);
                job.setClaimToken(null);
                outbox.save(new OutboxEvent(job.getBusinessId(), job.getId(), "scan.requested",
                        "{\"scanId\":" + job.getId() + "}"));
            }
        }
        return staleJobs.size();
    }

    public ScanDraftRecord findDraft(long scanId) {
        return drafts.findByScanJobId(scanId).map(ScanRepository::record).orElse(null);
    }

    @Transactional
    public ScanJobRecord lockForBusiness(String businessId, long id) {
        return jobs.findLockedByBusinessIdAndId(businessId, id).map(ScanRepository::record).orElse(null);
    }

    @Transactional
    public ScanDraftRecord lockDraft(long scanId) {
        return drafts.findLockedByScanJobId(scanId).map(ScanRepository::record).orElse(null);
    }

    @Transactional
    public void markPosted(long scanId, long oldVersion, long purchaseId, String editedJson,
                           BigDecimal calculatedTotal) {
        StoredScanDraft draft = drafts.findLockedByScanJobId(scanId).orElse(null);
        ScanJob job = jobs.findLockedById(scanId).orElse(null);
        if (draft == null || draft.getVersion() != oldVersion || draft.getPostedPurchaseId() != null) {
            throw new IllegalStateException("Scan draft changed during confirmation.");
        }
        if (job == null || !"REVIEW_REQUIRED".equals(job.getStatus())) {
            throw new IllegalStateException("Scan job changed during confirmation.");
        }
        draft.setEditedJson(editedJson);
        draft.setCalculatedTotal(calculatedTotal);
        draft.setVersion(oldVersion + 1);
        draft.setPostedPurchaseId(purchaseId);
        job.setStatus("POSTED");
        job.setErrorCode(null);
    }

    public List<ScanJobRecord> findExpiredImages(int retentionDays) {
        Instant cutoff = Instant.now().minusSeconds((long) retentionDays * 24 * 60 * 60);
        return jobs.findTop100ByKeepAttachmentFalseAndImageDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(cutoff)
                .stream().map(ScanRepository::record).toList();
    }

    @Transactional
    public void markImageDeleted(long scanId) {
        jobs.findById(scanId).filter(job -> !job.isKeepAttachment() && job.getImageDeletedAt() == null)
                .ifPresent(job -> job.setImageDeletedAt(Instant.now()));
    }

    private void finishAttempt(ScanClaim claim, String status, String errorCode) {
        attempts.findByScanJobIdAndAttemptNo(claim.job().id(), claim.attemptNo()).ifPresent(attempt -> {
            attempt.setStatus(status);
            attempt.setErrorCode(errorCode);
            attempt.setCompletedAt(Instant.now());
        });
    }

    private static boolean isTerminal(String status) {
        return "REVIEW_REQUIRED".equals(status) || "POSTED".equals(status)
                || "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    private static boolean ownsClaim(ScanJob job, ScanClaim claim) {
        return job != null && "PROCESSING".equals(job.getStatus())
                && claim.token().equals(job.getClaimToken());
    }

    private static ScanJobRecord record(ScanJob job) {
        return new ScanJobRecord(job.getId(), job.getBusinessId(), job.getCustomerId(), job.getObjectKey(),
                job.getSha256(), job.getMimeType(), job.getStatus(), job.getAttemptCount(), job.getErrorCode(),
                job.getUploadIdempotencyKey(), job.isKeepAttachment(), job.getCreatedAt(), job.getLockedAt(),
                job.getClaimToken(), job.getImageDeletedAt());
    }

    private static ScanDraftRecord record(StoredScanDraft draft) {
        return new ScanDraftRecord(draft.getId(), draft.getScanJobId(), draft.getExtractedJson(),
                draft.getEditedJson(), decimal(draft.getWrittenTotal()), decimal(draft.getCalculatedTotal()),
                draft.getFlagsJson(), draft.getVersion(), draft.getPostedPurchaseId());
    }

    private static String decimal(BigDecimal amount) {
        return amount == null ? null : amount.toPlainString();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Scan draft could not be saved.", exception);
        }
    }
}
