package com.baakio.utils;

import com.baakio.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class IndianPhoneNumbers {
    private IndianPhoneNumbers() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) throw invalid();
        String digits = value.trim().replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);
        if (!digits.matches("^[6-9][0-9]{9}$")) throw invalid();
        return "+91" + digits;
    }

    public static String fast2SmsNumber(String phoneE164) {
        return normalize(phoneE164).substring(3);
    }

    private static RuntimeException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE_NUMBER",
                "Enter a valid 10-digit Indian mobile number.");
    }
}
