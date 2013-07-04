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

import com.ovea.billing.*
import com.ovea.tadjin.util.Resource
import groovy.text.SimpleTemplateEngine

import java.util.logging.Logger

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class BangoConnector implements BillingCallback {

    private static final Logger LOGGER = Logger.getLogger(BangoConnector.name)

    private final BillingConfig config
    private final def bango = [
        url: 'https://webservices.bango.com/subscriptions/service.asmx',
        username: '',
        password: '',
        tmpl: [
            status: new SimpleTemplateEngine().createTemplate(Resource.from('classpath:com/ovea/billing/support/bango-status.xml').readAsString()),
            cancel: new SimpleTemplateEngine().createTemplate(Resource.from('classpath:com/ovea/billing/support/bango-cancel.xml').readAsString())
        ]
    ]

    BangoConnector(BillingConfig config) {
        this.config = config
        def pcfg = config.getPlatformConfig(BillingPlatform.bango)
        bango.username = pcfg.username
        bango.password = pcfg.password
    }

    @Override
    void onEvent(BillingEvent e) {
        LOGGER.fine('onEvent: ' + e)

        if (BillingPlatform.bango == e.platform) {
            switch (e.type) {

                case BillingEventType.BUY_REQUESTED:
                    e.data << [
                        from: e.request.remoteAddr,
                    ]
                    LOGGER.fine("BUY_REQUESTED: " + e.data.from)
                    break

                case BillingEventType.BUY_REQUEST_ACCEPTED:
                    if (!e.data.reference) {
                        throw new IllegalArgumentException('Missing reference')
                    }
                    LOGGER.fine("BUY_REQUEST_ACCEPTED: " + e.data.reference)
                    e.answer([
                        redirect: config.getProductConfig(BillingPlatform.bango, e.product).url + "&p=${e.data.reference}"
                    ])
                    e.type = BillingEventType.BUY_PENDING
                    break

                case BillingEventType.CALLBACK_REQUEST:

                    if (e.request.getParameter('errorCode')) {
                        throw new IllegalStateException("Billing error: message => " + e.request.getParameter('errorMessage')
                            + " payement method => " + e.request.getParameter('paymentMethodId')
                            + " payement type => " + e.request.getParameter('paymentMethodType')
                            + " network => " + e.request.getParameter("networkId"));
                    }

                    e.data << [
                        reference: e.request.getParameter('p'),
                        id: e.request.getParameter('subid'),
                        period: config.getProductConfig(e.product).period
                    ]
                    if (!e.data.id) {
                        throw new IllegalArgumentException('Missing subscription id')
                    }
                    LOGGER.fine("CALLBACK_REQUEST: " + e.data.reference + " - " + e.data.id)
                    e.data << status(e)
                    switch (e.data.status) {
                        case 'OK':
                            e.type = BillingEventType.CALLBACK_BUY_ACCEPTED
                            break
                        case 'WAITING':
                            e.type = BillingEventType.CALLBACK_BUY_PENDING
                            break
                        default:
                            e.type = BillingEventType.CALLBACK_BUY_REJECTED
                            break
                    }
                    break

                case BillingEventType.CANCEL_REQUEST_ACCEPTED:
                    if (!e.data.id) {
                        throw new IllegalArgumentException('Missing subscription id')
                    }
                    LOGGER.fine("CANCEL_REQUEST_ACCEPTED: " + e.data.id)
                    e.type = BillingEventType.CANCEL_COMPLETED
                    e.data << status(e)
                    if (e.data.status == 'OK' || e.data.status == 'WAITING') {
                        e.data << cancel(e)
                    }
                    break

                case BillingEventType.NOTIFICATION:
                    // See http://support.bango.com/attachments/token/uc9ue3hxumvvq0d/?name=EventNotification_v1.9.pdf
                    String xml = e.request.getParameter('XML')
                    def bangoEvents = new XmlSlurper().parseText(xml)
                    LOGGER.fine("NOTIFICATION: " + xml)
                    // we only check for subscription source
                    if (bangoEvents.source.toString() == 'SUBSCRIPTION') {
                        bangoEvents.eventList.event.each { evt ->
                            if (evt.action.toString() in ['CANCEL', 'REVOKE']) {
                                BillingEvent c = e.newChild(BillingEventType.CANCEL_COMPLETED)
                                c.data.id = evt.data.item.find { i -> i.@name.toString() == 'subscriptionId' }.value
                            }
                        }
                    }
                    e.answer(200)
                    break
            }
        }

    }

    def status(BillingEvent e) {
        def req = bango.tmpl.status.make([
            event: e,
            username: bango.username,
            password: bango.password,
        ]).toString()
        def res = IO.soapRequest(bango.url as String, req, [
            Host: 'webservices.bango.com',
            SOAPAction: 'com.bango.webservices.subscriptions/GetSubscriptionInfo',
            'Content-Type': 'text/xml;charset=UTF-8'
        ])
        return [
            /*
                OK              Success
                ACCESS_DENIED   Invalid username/password
                ACCESS_DENIED   Invalid client IP
                ACCESS_DENIED           Invalid access to specified subscription ID
                INVALID_SUBSCRIPTIONID Invalid subscription ID
                INTERNAL_ERROR  A problem on the server meant that the request could not be processed
                RETRYING        Renewal for this subscription period has been attempted, but failed. A retry will soon be attempted at the date now set as the next renewal date.
                UNCOLLECTED     All retry attempts for the latest renewal have failed, so this period has not been paid for.
                REVOKED         Subscription has been cancelled and any remaining paid- for time has been revoked.
                INACTIVE        When a subscription paid for using credit card has not been accessed by a user for at least 2 subscription periods
                WAITING         A payment collection attempt has been made, but the biller has not yet confirmed whether this was successful.
                CANCELLED       Subscription has been cancelled by you, the user, or possibly because the operator let Bango know the user’s phone number has been disconnected or recycled
                ERROR           Payment has failed.
            */
            status: res.Body.GetSubscriptionInfoResponse.GetSubscriptionInfoResult.responseCode as String
        ]
    }

    def cancel(BillingEvent e) {
        def req = bango.tmpl.cancel.make([
            event: e,
            username: bango.username,
            password: bango.password,
        ]).toString()
        def res = IO.soapRequest(bango.url as String, req, [
            Host: 'webservices.bango.com',
            SOAPAction: 'com.bango.webservices.subscriptions/CancelSubscription',
            'Content-Type': 'text/xml;charset=UTF-8'
        ])
        return [
            /*
                OK              Success
                ACCESS_DENIED   Invalid username/password
                ACCESS_DENIED   Invalid client IP address
                ACCESS_DENIED   Invalid access to specified subscription ID
                INVALID_SUBSCRIPTIONID  Invalid subscription ID
                ALREADY_CANCELLED   Subscription already cancelled
                INTERNAL_ERROR A problem on the server meant that the request could not be processed
            */
            status: res.Body.CancelSubscriptionResponse.CancelSubscriptionResult.responseCode as String
        ]
    }

}
