package com.baakio.service;

import com.baakio.dto.commitment.CommitmentResponse;
import com.baakio.dto.commitment.CreateCommitmentRequest;
import com.baakio.dto.commitment.RescheduleRequest;
import com.baakio.dto.commitment.RevisionResponse;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.model.CommitmentRevision;
import com.baakio.model.Commitment;
import com.baakio.repository.CommitmentRepository;
import com.baakio.repository.LedgerRepository;
import com.baakio.utils.MoneyAmounts;
import com.baakio.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;

@Service
public class CommitmentService {
    private final CommitmentRepository commitments;
    private final LedgerRepository ledger;
    private final IdempotencyService idempotency;

    public CommitmentService(CommitmentRepository commitments, LedgerRepository ledger, IdempotencyService idempotency) {
        this.commitments = commitments;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    @Transactional
    public CommitmentResponse create(AccountHolderPrincipal principal, long customerId,
            CreateCommitmentRequest request, String key) {
        validateKey(key);
        ledger.lockCustomer(principal.businessId(), customerId);
        OptionalLong prior = idempotency.existing(principal.businessId(), key, "COMMITMENT");
        if (prior.isPresent()) return get(principal.businessId(), customerId, prior.getAsLong());
        BigDecimal amount = MoneyAmounts.requireCents(request.amount(), "INVALID_COMMITMENT_AMOUNT");
        BigDecimal available = ledger.outstanding(principal.businessId(), customerId)
                .subtract(ledger.activeCommitmentRemaining(principal.businessId(), customerId));
        if (amount.compareTo(available) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "COMMITMENT_EXCEEDS_UNPROMISED_BALANCE", "The promise cannot exceed the amount that has not already been promised.");
        }
        long id = commitments.create(principal.businessId(), customerId, amount, request.dueDate(),
                clean(request.note()), principal.id());
        idempotency.record(principal.businessId(), key, "COMMITMENT", "COMMITMENT", id);
        audit(principal, id, "CREATED");
        return get(principal.businessId(), customerId, id);
    }

    @Transactional
    public CommitmentResponse reschedule(AccountHolderPrincipal principal, long commitmentId,
            RescheduleRequest request, String key) {
        validateKey(key);
        Commitment ref = findRef(principal.businessId(), commitmentId);
        ledger.lockCustomer(principal.businessId(), ref.getCustomerId());
        ref = findRef(principal.businessId(), commitmentId);
        OptionalLong prior = idempotency.existing(principal.businessId(), key, "RESCHEDULE");
        if (prior.isPresent()) return get(principal.businessId(), ref.getCustomerId(), prior.getAsLong());
        if (!"ACTIVE".equals(ref.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COMMITMENT_NOT_ACTIVE", "Only an active promise can be rescheduled.");
        }
        if (request.newDueDate().equals(ref.getCurrentDueDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMMITMENT_DATE_UNCHANGED", "Choose a different promised date.");
        }
        commitments.addRevisionAndUpdate(principal.businessId(), commitmentId, ref.getCurrentDueDate(),
                request.newDueDate(), clean(request.reason()), principal.id());
        idempotency.record(principal.businessId(), key, "RESCHEDULE", "COMMITMENT", commitmentId);
        audit(principal, commitmentId, "RESCHEDULED");
        return get(principal.businessId(), ref.getCustomerId(), commitmentId);
    }

    public List<CommitmentResponse> list(String businessId, long customerId) {
        return commitments.findIds(businessId, customerId).stream()
                .map(id -> get(businessId, customerId, id)).toList();
    }

    public CommitmentResponse get(String businessId, long customerId, long commitmentId) {
        Commitment commitment = commitments.findByBusinessCustomerAndId(businessId, customerId, commitmentId);
        if (commitment == null) throw ApiException.notFound();
        BigDecimal remaining = commitments.findRemainingAmount(commitmentId);
        LocalDate dueDate = commitment.getCurrentDueDate();
        String status = status(commitment.getStatus(), remaining, dueDate, ledger.businessDate(businessId));
        List<CommitmentRevision> revisionEntities = commitments.findRevisions(commitmentId);
        List<RevisionResponse> revisions = revisionEntities.stream().map(revision ->
                new RevisionResponse(revision.getOldDate(), revision.getNewDate(), revision.getReason(), revision.getCreatedAt())).toList();
        LocalDate originalDate = revisions.isEmpty() ? dueDate : revisionEntities.getFirst().getOldDate();
        return new CommitmentResponse(commitmentId, customerId,
                MoneyAmounts.text(commitment.getOriginalAmount()), MoneyAmounts.text(remaining),
                originalDate, dueDate, status, commitment.getNote(), revisions, commitment.getCreatedAt());
    }

    private Commitment findRef(String businessId, long id) {
        Commitment ref = commitments.findReference(businessId, id);
        if (ref == null) throw ApiException.notFound();
        return ref;
    }

    private String status(String stored, BigDecimal remaining, LocalDate due, LocalDate today) {
        if (!"ACTIVE".equals(stored) || remaining.signum() <= 0) return "FULFILLED";
        if (due.isBefore(today)) return "OVERDUE";
        if (due.equals(today)) return "DUE_TODAY";
        return "UPCOMING";
    }

    private void audit(AccountHolderPrincipal principal, long id, String action) {
        commitments.audit(principal.businessId(), principal.id(), id, action);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Provide a valid Idempotency-Key to safely save this action.");
        }
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
