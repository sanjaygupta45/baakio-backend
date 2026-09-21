package com.baakio.config;

import com.baakio.storage.DocumentStorage;
import com.baakio.storage.LocalStorageAdapter;
import com.baakio.storage.OciObjectStorageAdapter;
import com.oracle.bmc.objectstorage.ObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class DocumentStorageConfig {
    @Bean
    @ConditionalOnProperty(name = "baakio.storage.mode", havingValue = "local", matchIfMissing = true)
    DocumentStorage localStorage(@Value("${baakio.storage.local-directory}") String directory) {
        return new LocalStorageAdapter(directory);
    }

    @Bean
    @ConditionalOnProperty(name = "baakio.storage.mode", havingValue = "oci")
    DocumentStorage ociStorage(ObjectStorage client,
            @Value("${baakio.storage.oci.namespace}") String namespace,
            @Value("${baakio.storage.oci.bucket}") String bucket) {
        return new OciObjectStorageAdapter(client, namespace, bucket);
    }
}
