package com.baakio.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class GcsAuthenticationConfig {
    @Bean
    Storage gcsClient(@Value("${baakio.storage.gcs.project-id}") String projectId) {
        return StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    }
}
