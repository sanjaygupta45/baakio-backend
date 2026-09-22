package com.baakio.controller;

import com.baakio.dto.ledger.PurchaseResponse;
import com.baakio.dto.scan.ScanConfirmRequest;
import com.baakio.dto.scan.ScanResponse;
import com.baakio.dto.scan.ScanUploadResponse;
import com.baakio.service.BillScanService;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.dto.scan.StoredDocument;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;

@RestController
@RequestMapping("/api/v1")
public class ScanController {
    private final BillScanService scans;

    public ScanController(BillScanService scans) { this.scans = scans; }

    @PostMapping(value = "/customers/{customerId}/scans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScanUploadResponse> upload(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long customerId, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean keepAttachment,
            @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted().body(scans.upload(principal, customerId, file, keepAttachment, key));
    }

    @GetMapping("/scans/{scanId}")
    public ScanResponse status(@AuthenticationPrincipal AccountHolderPrincipal principal,
                               @PathVariable long scanId) {
        return scans.get(principal, scanId);
    }

    @GetMapping("/scans/{scanId}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal AccountHolderPrincipal principal,
                                        @PathVariable long scanId) {
        StoredDocument document = scans.source(principal, scanId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(document.contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=source-bill")
                .body(document.content());
    }

    @PostMapping("/scans/{scanId}/confirm")
    public PurchaseResponse confirm(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long scanId, @RequestHeader("Idempotency-Key") String key,
            @Valid @org.springframework.web.bind.annotation.RequestBody ScanConfirmRequest body) {
        return scans.confirm(principal, scanId, body, key);
    }
}
