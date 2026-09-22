package com.baakio.service;

import com.baakio.dto.scan.StoredDocument;

public interface DocumentStorage {
    void put(String objectKey, byte[] content, String contentType);
    StoredDocument get(String objectKey);
    void delete(String objectKey);
}
