package com.baakio.config;

import com.baakio.service.DocumentStorage;
import com.baakio.service.GcsObjectStorageAdapter;
import com.google.cloud.storage.Storage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@Profile("!test")
public class DocumentStorageConfig {
    @Bean
    DocumentStorage gcsStorage(Storage client,
            @Value("${baakio.storage.gcs.bucket}") String bucket) {
        return new GcsObjectStorageAdapter(client, bucket);
    }
}
