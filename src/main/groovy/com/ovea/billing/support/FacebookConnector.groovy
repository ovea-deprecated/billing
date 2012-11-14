package com.ovea.billing.support

import com.ovea.billing.BillingCallback
import com.ovea.billing.BillingConfig
import com.ovea.billing.BillingEvent

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class FacebookConnector implements BillingCallback  {

    final BillingConfig config

    FacebookConnector(BillingConfig config) {
        this.config = config
    }

    @Override
    void onEvent(BillingEvent e) {

    }
}
