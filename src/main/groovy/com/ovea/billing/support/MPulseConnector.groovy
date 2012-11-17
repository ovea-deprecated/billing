/**
 * Copyright (C) 2011 Ovea <dev@ovea.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.ovea.billing.BillingCallback
import com.ovea.billing.BillingConfig
import com.ovea.billing.BillingEvent
import com.ovea.billing.BillingEventType
import com.ovea.billing.BillingPlatform
import com.ovea.billing.IO
import com.ovea.tadjin.util.Resource
import groovy.text.SimpleTemplateEngine
import org.apache.commons.codec.binary.Base64

import java.util.logging.Logger

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class MPulseConnector implements BillingCallback {

    private static final Logger LOGGER = Logger.getLogger(MPulseConnector.name)

    final def mpulse = [
        callback: '',
        url: 'http://gateway.mpulse.eu/wapbilling/france/basicauth',
        auth: '',
        tmpl: [
            buy: new SimpleTemplateEngine().createTemplate(Resource.from('classpath:com/ovea/billing/support/mpulse-buy.xml').readAsString()),
            status: new SimpleTemplateEngine().createTemplate(Resource.from('classpath:com/ovea/billing/support/mpulse-status.xml').readAsString()),
            cancel: new SimpleTemplateEngine().createTemplate(Resource.from('classpath:com/ovea/billing/support/mpulse-cancel.xml').readAsString())
        ]
    ]

    MPulseConnector(BillingConfig config) {
        mpulse.callback = config.url + '/callback/' + BillingPlatform.mpulse
        mpulse.auth = Base64.encodeBase64String((config.env.getString("mpulse.username") + ":" + config.env.getString("mpulse.password")).bytes)
    }

    @Override
    void onEvent(BillingEvent e) {
        LOGGER.fine('onEvent: ' + e)

        if (BillingPlatform.mpulse in e.platforms) {
            switch (e.type) {

                case BillingEventType.BUY_REQUESTED:
                    e.data << [
                        from: e.request.remoteAddr,
                        callback: mpulse.callback
                    ]
                    break

                case BillingEventType.BUY_REQUEST_ACCEPTED:
                    if (!e.data.reference) {
                        throw new IllegalArgumentException('Missing reference')
                    }
                    e.data << buy(e)
                    e.answer([
                        redirect: e.data.redirect
                    ])
                    e.type = BillingEventType.BUY_PENDING
                    break

                case BillingEventType.CANCEL_REQUEST_ACCEPTED:
                    if (!e.data.id) {
                        throw new IllegalArgumentException('Missing subscription id')
                    }
                    e.type = BillingEventType.CANCEL_COMPLETED
                    e.data << status(e)
                    if (e.data.status == 'ACTIVE') {
                        e.data << cancel(e)
                        if (e.data.redirect) {
                            e.type = BillingEventType.CANCEL_PENDING
                        }
                    }
                    e.answer([
                        redirect: e.data.redirect
                    ])
                    break;

                case BillingEventType.CALLBACK_REQUEST:
                    e.data << [
                        id: e.request.getParameter('tid')
                    ]
                    if (!e.data.id) {
                        throw new IllegalArgumentException('Missing subscription id')
                    }
                    e.data << status(e)
                    if (e.data.status == 'ACTIVE') {
                        e.type = BillingEventType.CALLBACK_BUY_ACCEPTED
                    } else if (e.data.status == 'PENDING') {
                        e.type = BillingEventType.CALLBACK_BUY_PENDING
                    } else if (e.data.status in ['CANCEL', 'STOPPED']) {
                        e.type = BillingEventType.CALLBACK_BUY_REJECTED
                    }
                    break

                case BillingEventType.RECOVER_REQUEST:
                    if (!e.data.id) {
                        throw new IllegalArgumentException('Missing subscription id')
                    }
                    e.data << status(e)
                    if (e.data.status == 'ACTIVE') {
                        e.type = BillingEventType.RECOVER_ACCEPTED
                    }
                    break

                case BillingEventType.RENEWAL_REQUEST:
                    if (!e.data.id) {
                        throw new IllegalArgumentException('Missing subscription id')
                    }
                    e.data << status(e)
                    if (e.data.status == 'ACTIVE') {
                        e.type = BillingEventType.RENEWAL_ACCEPTED
                    } else if (e.data.status in ['CANCEL', 'STOPPED']) {
                        e.type = BillingEventType.RENEWAL_REJECTED
                    }
                    break
            }
        }

    }

    def buy(BillingEvent e) {
        def req = mpulse.tmpl.buy.make([
            event: e
        ]).toString()
        def res = IO.soapRequest(mpulse.url as String, req, [
            Host: 'gateway.mpulse.eu',
            Authorization: 'Basic ' + mpulse.auth
        ])
        return [
            id: res.Body.startSubscriptionExtendedResponse.'return'.id as String,
            redirect: res.Body.startSubscriptionExtendedResponse.'return'.redirectUrl as String
        ]
    }

    def status(BillingEvent e) {
        def req = mpulse.tmpl.status.make([
            event: e
        ]).toString()
        def res = IO.soapRequest(mpulse.url as String, req, [
            Host: 'gateway.mpulse.eu',
            Authorization: 'Basic ' + mpulse.auth
        ])
        return [
            // ACTIVE, CANCEL, STOPPED, PENDING
            status: res.Body.getSubscriptionStatusResponse.'return'.status as String
        ]
    }

    def cancel(BillingEvent e) {
        def req = mpulse.tmpl.cancel.make([
            event: e
        ]).toString()
        def res = IO.soapRequest(mpulse.url as String, req, [
            Host: 'gateway.mpulse.eu',
            Authorization: 'Basic ' + mpulse.auth
        ])
        return [
            redirect: res.Body.cancelSubscriptionResponse.'return'.redirectUrl as String
        ]
    }

}
