package com.baakio.config;

import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class GcsStorageHealthConfig {
    @Bean
    HealthIndicator gcsStorageHealthIndicator(Storage storage,
            @Value("${baakio.storage.gcs.bucket}") String bucket) {
        return () -> {
            try {
                storage.list(bucket, Storage.BlobListOption.pageSize(1)).iterateAll().iterator().hasNext();
                return Health.up().build();
            } catch (Exception exception) {
                return Health.down().build();
            }
        };
    }
}
