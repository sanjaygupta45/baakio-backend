package com.baakio.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecordId implements Serializable {
    private String businessId;
    private String idempotencyKey;
}
