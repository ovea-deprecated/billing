/**
 * Copyright (C) 2011 Ovea <dev@ovea.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ovea.billing.support

import com.ovea.billing.*

import java.util.logging.Logger

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class BangoConnector implements BillingCallback {

    private static final Logger LOGGER = Logger.getLogger(BangoConnector.name)

    private final BillingConfig config
    private final def bango = [
        callback: '/callback/' + BillingPlatform.bango
    ]

    BangoConnector(BillingConfig config) {
        bango.callback = config.url + bango.callback
        this.config = config
    }

    @Override
    void onEvent(BillingEvent e) {
        LOGGER.fine('onEvent: ' + e)

        if (BillingPlatform.bango in e.platforms) {
            switch (e.type) {

                case BillingEventType.BUY_REQUESTED:
                    e.data << [
                        from: e.request.remoteAddr,
                    ]
                    break

                case BillingEventType.BUY_REQUEST_ACCEPTED:
                    if (!e.data.reference) {
                        throw new IllegalArgumentException('Missing reference')
                    }
                    e.answer([
                        redirect: config.getProductConfig(BillingPlatform.bango, e.product).url + "&=p=${e.data.reference}"
                    ])
                    e.type = BillingEventType.BUY_PENDING
                    break

                case BillingEventType.CALLBACK_REQUEST:
                    if (!e.request.getParameter('u') || !e.request.getParameter('t')) {
                        throw new IllegalArgumentException('Missing subscription id and user info')
                    }
                    e.data << [
                        reference: e.request.getParameter('p'),
                        id: "${e.request.getParameter('u')}|${e.request.getParameter('t')}", // BangoUserId|BangoSubscriptionToken
                        period: config.getProductConfig(e.product).period
                    ]
                    e.type = BillingEventType.CALLBACK_BUY_ACCEPTED
                    break

                case BillingEventType.CANCEL_REQUEST_ACCEPTED:
                    if (!e.data.id) {
                        throw new IllegalArgumentException('Missing subscription id')
                    }
                    e.type = BillingEventType.CANCEL_COMPLETED
                    //TODO - call bango
                    /*e.data << status(e)
                    if (e.data.status == 'ACTIVE') {
                        e.data << cancel(e)
                        if (e.data.redirect) {
                            e.type = BillingEventType.CANCEL_PENDING
                        }
                    }*/
                    e.answer([
                        redirect: e.data.redirect
                    ])
                    break
            }
        }

    }
}
