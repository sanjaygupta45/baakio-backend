package com.baakio.utils;

import com.baakio.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyAmounts {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private MoneyAmounts() {}

    public static BigDecimal requireCents(BigDecimal value, String code) {
        if (value == null || value.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Enter an amount greater than zero.");
        }
        if (value.precision() - value.scale() > 17) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "This amount is larger than the account supports.");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Amounts can have at most two decimal places.");
        }
    }

    public static BigDecimal nonNegativeCents(BigDecimal value, String code) {
        if (value == null || value.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Enter a valid non-negative amount.");
        }
        if (value.precision() - value.scale() > 17) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "This amount is larger than the account supports.");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Amounts can have at most two decimal places.");
        }
    }

    public static BigDecimal zero() { return ZERO; }
    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }
    public static String text(BigDecimal amount) { return amount.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
}
