package com.app.SalesInventory;

public interface PaymentProcessor {

    interface Callback {
        void onSuccess(String referenceId);
        void onFailure(String error);
    }

    void processPayment(double amount,
                        String method,
                        Callback callback);
}