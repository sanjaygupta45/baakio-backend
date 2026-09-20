package com.baakio.dto.commitment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CommitmentResponse(
        long id,
        long customerId,
        String originalAmount,
        String remaining,
        LocalDate originalDueDate,
        LocalDate dueDate,
        String status,
        String note,
        List<RevisionResponse> revisions,
        Instant createdAt
) {}
