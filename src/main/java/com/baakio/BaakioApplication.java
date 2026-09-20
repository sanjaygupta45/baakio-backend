package com.baakio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.baakio.notification.fast2sms.Fast2SmsProperties;

@SpringBootApplication
@EnableConfigurationProperties(Fast2SmsProperties.class)
public class BaakioApplication {

	public static void main(String[] args) {
		SpringApplication.run(BaakioApplication.class, args);
	}

}
