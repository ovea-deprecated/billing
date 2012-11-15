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

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class MPulseConnector implements BillingCallback {

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
                    e.data << status(e)
                    if (e.data.status == 'ACTIVE') {
                        e.type = BillingEventType.CALLBACK_REQUEST_ACCEPTED
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
            redirect: res?.Body?.cancelSubscriptionResponse?.'return'?.redirectUrl
        ]
    }

}
