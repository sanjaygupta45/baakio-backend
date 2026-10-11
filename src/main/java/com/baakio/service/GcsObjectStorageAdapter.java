package com.baakio.service;

import com.baakio.dto.scan.StoredDocument;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

public class GcsObjectStorageAdapter implements DocumentStorage {
    private final Storage client;
    private final String bucket;

    public GcsObjectStorageAdapter(Storage client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        var blob = BlobInfo.newBuilder(BlobId.of(bucket, objectKey))
                .setContentType(contentType)
                .build();
        client.create(blob, content);
    }

    @Override
    public StoredDocument get(String objectKey) {
        var blob = client.get(BlobId.of(bucket, objectKey));
        if (blob == null) {
            throw new IllegalStateException("Private GCS document was not found.");
        }
        return new StoredDocument(blob.getContent(), blob.getContentType());
    }

    @Override
    public void delete(String objectKey) {
        client.delete(BlobId.of(bucket, objectKey));
    }
}
