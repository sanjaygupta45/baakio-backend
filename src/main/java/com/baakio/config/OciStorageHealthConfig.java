package com.baakio.config;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.GetBucketRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class OciStorageHealthConfig {
    @Bean
    HealthIndicator ociStorageHealthIndicator(ObjectStorage storage,
                                             @Value("${baakio.storage.oci.namespace}") String namespace,
                                             @Value("${baakio.storage.oci.bucket}") String bucket) {
        return () -> {
            try {
                storage.getBucket(GetBucketRequest.builder()
                        .namespaceName(namespace).bucketName(bucket).build());
                return Health.up().build();
            } catch (Exception exception) {
                return Health.down().build();
            }
        };
    }
}
