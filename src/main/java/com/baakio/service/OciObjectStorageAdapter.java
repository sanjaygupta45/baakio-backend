package com.baakio.service;

import com.baakio.dto.scan.StoredDocument;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class OciObjectStorageAdapter implements DocumentStorage {
    private final ObjectStorage client;
    private final String namespace;
    private final String bucket;

    public OciObjectStorageAdapter(ObjectStorage client,
                                   @Value("${baakio.storage.oci.namespace}") String namespace,
                                   @Value("${baakio.storage.oci.bucket}") String bucket) {
        this.client = client;
        this.namespace = namespace;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder()
                .namespaceName(namespace).bucketName(bucket).objectName(objectKey)
                .contentType(contentType).contentLength((long) content.length)
                .putObjectBody(new ByteArrayInputStream(content)).build());
    }

    @Override
    public StoredDocument get(String objectKey) {
        var response = client.getObject(GetObjectRequest.builder()
                .namespaceName(namespace).bucketName(bucket).objectName(objectKey).build());
        try (var stream = response.getInputStream()) {
            return new StoredDocument(stream.readAllBytes(), response.getContentType());
        } catch (IOException exception) {
            throw new IllegalStateException("Private OCI document could not be read.", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(DeleteObjectRequest.builder()
                .namespaceName(namespace).bucketName(bucket).objectName(objectKey).build());
    }
}
