package com.baakio.config;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;

@Configuration
@Profile("!test")
public class OciAuthenticationConfig {
    @Bean(destroyMethod = "close")
    ObjectStorage ociObjectStorage(@Value("${baakio.storage.oci.config-file:}") String configFile,
                                   @Value("${baakio.storage.oci.profile:DEFAULT}") String profile,
                                   @Value("${baakio.storage.oci.region}") String region,
                                   @Value("${baakio.storage.oci.auth-mode:config_file}") String authMode) throws IOException {
        if ("instance_principal".equals(authMode)) {
            return ObjectStorageClient.builder().region(Region.fromRegionId(region))
                    .build(InstancePrincipalsAuthenticationDetailsProvider.builder().build());
        }
        if (!"config_file".equals(authMode)) {
            throw new IllegalArgumentException("Unsupported OCI authentication mode: " + authMode);
        }
        var config = configFile.isBlank()
                ? ConfigFileReader.parseDefault(profile)
                : ConfigFileReader.parse(configFile, profile);
        var provider = new ConfigFileAuthenticationDetailsProvider(config);
        return ObjectStorageClient.builder().region(Region.fromRegionId(region)).build(provider);
    }
}
