package com.ovea.billing

enum BillingPlatform {

    facebook,
    paypal,
    mpulse

    static BillingPlatform from(String platform) {
        try {
            return BillingPlatform.valueOf(BillingPlatform, platform)
        } catch (e) {
            return null
        }
    }
}
