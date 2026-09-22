package com.baakio.config;

import com.baakio.service.DocumentStorage;
import com.baakio.service.OciObjectStorageAdapter;
import com.oracle.bmc.objectstorage.ObjectStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@Profile("!test")
public class DocumentStorageConfig {
    @Bean
    DocumentStorage ociStorage(ObjectStorage client,
            @Value("${baakio.storage.oci.namespace}") String namespace,
            @Value("${baakio.storage.oci.bucket}") String bucket) {
        return new OciObjectStorageAdapter(client, namespace, bucket);
    }
}
