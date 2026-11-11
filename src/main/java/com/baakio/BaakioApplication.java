package com.baakio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.baakio.notification.fast2sms.Fast2SmsProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(Fast2SmsProperties.class)
@EnableScheduling
public class BaakioApplication {

	public static void main(String[] args) {
		SpringApplication.run(BaakioApplication.class, args);
	}

}
