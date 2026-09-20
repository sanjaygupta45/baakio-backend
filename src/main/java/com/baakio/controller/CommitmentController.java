package com.baakio.controller;

import com.baakio.dto.commitment.CommitmentResponse;
import com.baakio.dto.commitment.CreateCommitmentRequest;
import com.baakio.dto.commitment.RescheduleRequest;
import com.baakio.service.CommitmentService;
import com.baakio.service.CustomerService;
import com.baakio.security.AccountHolderPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CommitmentController {
    private final CommitmentService commitments;
    private final CustomerService customers;

    public CommitmentController(CommitmentService commitments, CustomerService customers) {
        this.commitments = commitments;
        this.customers = customers;
    }

    @PostMapping("/customers/{customerId}/commitments")
    public CommitmentResponse create(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long customerId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateCommitmentRequest body) {
        return commitments.create(principal, customerId, body, key);
    }

    @GetMapping("/customers/{customerId}/commitments")
    public List<CommitmentResponse> list(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long customerId) {
        customers.ensureOwned(principal.businessId(), customerId);
        return commitments.list(principal.businessId(), customerId);
    }

    @PostMapping("/commitments/{commitmentId}/reschedule")
    public CommitmentResponse reschedule(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long commitmentId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RescheduleRequest body) {
        return commitments.reschedule(principal, commitmentId, body, key);
    }
}
