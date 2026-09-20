package com.baakio.controller;

import com.baakio.dto.ledger.CreatePaymentRequest;
import com.baakio.dto.ledger.CreatePurchaseRequest;
import com.baakio.dto.ledger.LedgerResponse;
import com.baakio.dto.ledger.OpeningBalanceRequest;
import com.baakio.dto.ledger.OpeningBalanceResponse;
import com.baakio.dto.ledger.PaymentHistoryResponse;
import com.baakio.dto.ledger.PaymentResponse;
import com.baakio.dto.ledger.PurchasePage;
import com.baakio.dto.ledger.PurchaseResponse;
import com.baakio.service.LedgerReadService;
import com.baakio.service.FinancialPostingService;
import com.baakio.service.CustomerService;
import com.baakio.security.AccountHolderPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {
    private final FinancialPostingService postings;
    private final LedgerReadService reads;
    private final CustomerService customers;

    public LedgerController(FinancialPostingService postings, LedgerReadService reads, CustomerService customers) {
        this.postings = postings;
        this.reads = reads;
        this.customers = customers;
    }

    @PostMapping("/customers/{customerId}/opening-balance")
    public OpeningBalanceResponse opening(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long customerId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody OpeningBalanceRequest body) {
        return postings.postOpeningBalance(principal, customerId, body, key);
    }

    @PostMapping("/customers/{customerId}/purchases")
    public PurchaseResponse purchase(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long customerId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreatePurchaseRequest body) {
        return postings.postPurchase(principal, customerId, body, key);
    }

    @GetMapping("/customers/{customerId}/purchases")
    public PurchasePage purchases(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        customers.ensureOwned(principal.businessId(), customerId);
        return reads.purchases(principal.businessId(), customerId, from, to, page, size);
    }

    @PostMapping("/customers/{customerId}/payments")
    public PaymentResponse payment(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long customerId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreatePaymentRequest body) {
        return postings.postPayment(principal, customerId, body, key);
    }

    @GetMapping("/customers/{customerId}/payments")
    public List<PaymentHistoryResponse> payments(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long customerId) {
        customers.ensureOwned(principal.businessId(), customerId);
        return reads.payments(principal.businessId(), customerId);
    }

    @GetMapping("/customers/{customerId}/ledger")
    public LedgerResponse ledger(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        customers.ensureOwned(principal.businessId(), customerId);
        return reads.statement(principal.businessId(), customerId, from, to);
    }
}
