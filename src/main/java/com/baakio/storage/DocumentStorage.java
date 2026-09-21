package com.baakio.storage;

public interface DocumentStorage {
    void put(String objectKey, byte[] content, String contentType);
    StoredDocument get(String objectKey);
    void delete(String objectKey);

    record StoredDocument(byte[] content, String contentType) {}
}
