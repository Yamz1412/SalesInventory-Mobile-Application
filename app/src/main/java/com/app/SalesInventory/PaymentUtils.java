package com.app.SalesInventory;

import java.math.BigDecimal;

public class PaymentUtils {
    public static final BigDecimal PAYMENT_MAX = new BigDecimal("1000000.00");
    public static boolean validatePayment(BigDecimal input, BigDecimal remaining) {
        if (input == null) return false;
        if (input.compareTo(BigDecimal.ZERO) <= 0) return false;
        if (remaining != null && input.compareTo(remaining) > 0) return false;
        if (input.compareTo(PAYMENT_MAX) > 0) return false;
        return true;
    }
}