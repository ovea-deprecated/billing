package com.ovea.billing

public interface BillingCallback {
    void onEvent(BillingEvent e);
}