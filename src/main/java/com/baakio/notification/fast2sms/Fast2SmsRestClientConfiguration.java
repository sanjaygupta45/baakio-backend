package com.baakio.notification.fast2sms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class Fast2SmsRestClientConfiguration {

    @Bean
    RestClient fast2SmsRestClient(RestClient.Builder builder, Fast2SmsProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }
}
