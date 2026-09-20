package com.baakio.service;

import com.baakio.repository.IdempotencyRepository;
import com.baakio.model.IdempotencyRecord;
import com.baakio.model.IdempotencyRecordId;
import com.baakio.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.OptionalLong;

@Service
public class IdempotencyService {
    private final IdempotencyRepository idempotency;

    public IdempotencyService(IdempotencyRepository idempotency) { this.idempotency = idempotency; }

    public OptionalLong existing(String businessId, String key, String operation) {
        return idempotency.findById(new IdempotencyRecordId(businessId, key)).map(record -> {
            if (!operation.equals(record.getOperation())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "This request key was already used for another action.");
            }
            return OptionalLong.of(record.getResourceId());
        }).orElseGet(OptionalLong::empty);
    }

    public void record(String businessId, String key, String operation, String resourceType, long resourceId) {
        idempotency.save(new IdempotencyRecord(businessId, key, operation, resourceType, resourceId));
    }
}
