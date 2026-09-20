package com.baakio.repository;

import com.baakio.model.IdempotencyRecord;
import com.baakio.model.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {
}
