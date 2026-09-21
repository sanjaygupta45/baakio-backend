package com.baakio.scan.application;

import com.baakio.dto.ledger.CreatePurchaseRequest;
import com.baakio.dto.ledger.PurchaseLineRequest;
import com.baakio.dto.ledger.PurchaseResponse;
import com.baakio.exception.ApiException;
import com.baakio.scan.ai.ScanDraftFactory;
import com.baakio.scan.api.ScanConfirmRequest;
import com.baakio.scan.api.ScanResponse;
import com.baakio.scan.api.ScanUploadResponse;
import com.baakio.scan.persistence.ScanDraft;
import com.baakio.scan.persistence.ScanDraftLine;
import com.baakio.scan.persistence.ScanDraftRecord;
import com.baakio.scan.persistence.ScanJobRecord;
import com.baakio.scan.persistence.ScanRepository;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.service.CustomerService;
import com.baakio.service.FinancialPostingService;
import com.baakio.service.IdempotencyService;
import com.baakio.storage.DocumentStorage;
import com.baakio.utils.MoneyAmounts;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

@Service
public class ScanApplicationService {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 10_000;
    private final ScanRepository scans;
    private final CustomerService customers;
    private final DocumentStorage storage;
    private final FinancialPostingService postings;
    private final IdempotencyService idempotency;
    private final ObjectMapper mapper;

    public ScanApplicationService(ScanRepository scans, CustomerService customers, DocumentStorage storage,
                                  FinancialPostingService postings, IdempotencyService idempotency,
                                  ObjectMapper mapper) {
        this.scans = scans;
        this.customers = customers;
        this.storage = storage;
        this.postings = postings;
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    public ScanUploadResponse upload(AccountHolderPrincipal principal, long customerId, MultipartFile file,
                                     boolean keepAttachment, String key) {
        validateKey(key);
        customers.ensureOwned(principal.businessId(), customerId);
        byte[] content = readAndValidate(file);
        String mimeType = detectMime(content);
        validateImageDimensions(content);
        String hash = sha256(content);
        Optional<Long> previousId = scans.findUploadByKey(principal.businessId(), key);
        if (previousId.isPresent()) {
            ScanJobRecord previous = scans.findForBusiness(principal.businessId(), previousId.get()).orElseThrow(ApiException::notFound);
            if (previous.customerId() != customerId || !previous.sha256().equals(hash)) throw reusedKey();
            return new ScanUploadResponse(previous.id(), previous.status(), previous.createdAt());
        }

        String objectKey = principal.businessId() + "/scans/" + UUID.randomUUID();
        storage.put(objectKey, content, mimeType);
        try {
            ScanJobRecord created = scans.createQueued(principal.businessId(), customerId, objectKey,
                    hash, mimeType, key, keepAttachment);
            return new ScanUploadResponse(created.id(), created.status(), created.createdAt());
        } catch (DataIntegrityViolationException exception) {
            storage.delete(objectKey);
            Optional<Long> raced = scans.findUploadByKey(principal.businessId(), key);
            if (raced.isPresent()) {
                ScanJobRecord previous = scans.findForBusiness(principal.businessId(), raced.get()).orElseThrow(ApiException::notFound);
                if (previous.customerId() != customerId || !previous.sha256().equals(hash)) throw reusedKey();
                return new ScanUploadResponse(previous.id(), previous.status(), previous.createdAt());
            }
            throw exception;
        } catch (RuntimeException exception) {
            storage.delete(objectKey);
            throw exception;
        }
    }

    public ScanResponse get(AccountHolderPrincipal principal, long scanId) {
        ScanJobRecord job = scans.findForBusiness(principal.businessId(), scanId).orElseThrow(ApiException::notFound);
        ScanDraftRecord draftRecord = scans.findDraft(scanId);
        ScanDraft draft = null;
        Long version = null;
        if (draftRecord != null) {
            try {
                draft = mapper.readValue(draftRecord.editedJson(), ScanDraft.class);
                version = draftRecord.version();
            } catch (JacksonException exception) {
                throw new IllegalStateException("Saved scan draft could not be read.", exception);
            }
        }
        return new ScanResponse(job.id(), job.customerId(), job.status(), job.attemptCount(), job.errorCode(),
                job.imageDeletedAt() == null, job.createdAt(), version, draft);
    }

    public DocumentStorage.StoredDocument source(AccountHolderPrincipal principal, long scanId) {
        ScanJobRecord job = scans.findForBusiness(principal.businessId(), scanId).orElseThrow(ApiException::notFound);
        if (job.imageDeletedAt() != null) throw ApiException.notFound();
        return storage.get(job.objectKey());
    }

    @Transactional
    public PurchaseResponse confirm(AccountHolderPrincipal principal, long scanId,
                                    ScanConfirmRequest request, String key) {
        validateKey(key);
        ScanJobRecord job = scans.lockForBusiness(principal.businessId(), scanId);
        if (job == null) throw ApiException.notFound();
        OptionalLong prior = idempotency.existing(principal.businessId(), key, "SCAN_CONFIRM");
        if (prior.isPresent() && prior.getAsLong() != scanId) throw reusedKey();
        if ("POSTED".equals(job.status())) {
            ScanDraftRecord posted = scans.lockDraft(scanId);
            if (posted == null || posted.postedPurchaseId() == null) throw ApiException.notFound();
            return postings.purchaseResponse(principal.businessId(), job.customerId(), posted.postedPurchaseId());
        }
        if (!"REVIEW_REQUIRED".equals(job.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "SCAN_NOT_READY", "This bill is not ready to confirm. Refresh the scan status and try again.");
        }
        ScanDraftRecord current = scans.lockDraft(scanId);
        if (current == null) throw ApiException.notFound();
        if (current.version() != request.version()) {
            throw new ApiException(HttpStatus.CONFLICT, "SCAN_DRAFT_VERSION_CONFLICT", "This bill draft changed. Refresh it and review the latest values.");
        }

        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        List<ScanDraftLine> finalLines = request.lines().stream().map(line -> {
            BigDecimal lineTotal = MoneyAmounts.lineTotal(line.quantity(), line.unitPrice());
            return new ScanDraftLine(line.name().trim(), line.quantity().stripTrailingZeros().toPlainString(),
                    clean(line.unit()), line.unitPrice().setScale(2).toPlainString(),
                    MoneyAmounts.text(lineTotal), List.of());
        }).toList();
        for (PurchaseLineRequest line : request.lines()) {
            subtotal = subtotal.add(MoneyAmounts.lineTotal(line.quantity(), line.unitPrice())).setScale(2);
        }
        BigDecimal discount = MoneyAmounts.nonNegativeCents(request.discount() == null ? BigDecimal.ZERO : request.discount(), "INVALID_DISCOUNT");
        BigDecimal total = subtotal.subtract(discount).setScale(2);
        if (total.signum() <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "PURCHASE_TOTAL_INVALID", "The purchase total after discount must be greater than zero.");
        ScanDraft oldDraft;
        try { oldDraft = mapper.readValue(current.editedJson(), ScanDraft.class); }
        catch (JacksonException exception) { throw new IllegalStateException("Saved scan draft could not be read.", exception); }
        ScanDraft reviewed = new ScanDraft(request.occurredOn(), clean(request.description()), MoneyAmounts.text(discount),
                oldDraft.writtenTotal(), MoneyAmounts.text(total), finalLines, oldDraft.flags());
        CreatePurchaseRequest purchase = new CreatePurchaseRequest("ITEMIZED", request.occurredOn(),
                request.description(), null, discount, request.lines(), request.paidNow());
        String financialKey = "scan.purchase." + scanId;
        PurchaseResponse posted = postings.postPurchase(principal, job.customerId(), purchase, financialKey);
        try {
            scans.markPosted(scanId, current.version(), posted.id(), mapper.writeValueAsString(reviewed), total);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Reviewed scan draft could not be saved.", exception);
        }
        if (prior.isEmpty()) idempotency.record(principal.businessId(), key, "SCAN_CONFIRM", "SCAN", scanId);
        return posted;
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "SCAN_IMAGE_REQUIRED", "Choose one bill image to upload.");
        if (file.getSize() > MAX_IMAGE_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "SCAN_IMAGE_TOO_LARGE", "Choose an image smaller than 10 MB.");
        try {
            byte[] content = file.getBytes();
            if (content.length == 0 || content.length > MAX_IMAGE_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "SCAN_IMAGE_TOO_LARGE", "Choose an image smaller than 10 MB.");
            detectMime(content);
            return content;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCAN_IMAGE_READ_FAILED", "The bill image could not be read. Choose it again.");
        }
    }

    private String detectMime(byte[] content) {
        if (content.length >= 8 && (content[0] & 0xff) == 0x89 && content[1] == 0x50
                && content[2] == 0x4e && content[3] == 0x47 && content[4] == 0x0d
                && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a) return "image/png";
        if (content.length >= 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) return "image/jpeg";
        if (content.length >= 12 && new String(content, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(content, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "SCAN_IMAGE_TYPE_UNSUPPORTED", "Upload a JPEG, PNG, or WebP image.");
    }

    private void validateImageDimensions(byte[] content) {
        try (var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(content))) {
            if (input == null) return;
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > 40_000_000L) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "SCAN_IMAGE_DIMENSIONS_INVALID", "Choose a smaller image.");
                }
            } finally { reader.dispose(); }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCAN_IMAGE_INVALID", "The selected file is not a readable image.");
        }
    }

    private String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception exception) { throw new IllegalStateException("Image fingerprint is unavailable.", exception); }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Provide a valid Idempotency-Key to safely save this action.");
        }
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private ApiException reusedKey() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "This request key was already used for another action.");
    }
}
