package com.baakio.notification.fast2sms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "baakio.notifications.fast2sms")
public class Fast2SmsProperties {
    private String baseUrl;
    private String apiKey;
    private String otpTemplateId;
    private String otpVariablesTemplate;
    private String quickSmsRoute;
    private String whatsappVersion;
    private String whatsappPhoneNumberId;
    private String whatsappOtpTemplateName;
    private String whatsappOtpButtonIndex;
}
