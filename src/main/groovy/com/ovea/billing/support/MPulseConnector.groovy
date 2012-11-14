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
            buy: new SimpleTemplateEngine().createTemplate(Resource.from('classpath:com/ovea/billing/support/mpulse-buy.xml').readAsString())
        ]
    ]

    MPulseConnector(BillingConfig config) {
        mpulse.callback = config.url + '/callback/' + BillingPlatform.mpulse
        mpulse.auth = Base64.encodeBase64String((config.env.getString("mpulse.username") + ":" + config.env.getString("mpulse.password")).bytes)
    }

    @Override
    void onEvent(BillingEvent e) {
        switch (e.type) {
            case BillingEventType.BUY_REQUESTED:
                e.data << [
                    from: e.request.remoteAddr,
                    callback: mpulse.callback
                ]
                break
            case BillingEventType.BUY_ACCEPTED:
                if (!e.data.reference) {
                    throw new IllegalArgumentException('Missing reference')
                }
                def req = mpulse.tmpl.buy.make([
                    event: e
                ]).toString()
                def res = IO.soapRequest(mpulse.url as String, req, [
                    Host: 'gateway.mpulse.eu',
                    Authorization: 'Basic ' + mpulse.auth
                ])
                e.data << [
                    id: res.Body.startSubscriptionExtendedResponse.'return'.id as String,
                    redirect: res.Body.startSubscriptionExtendedResponse.'return'.redirectUrl as String
                ]
                e.answer([
                    redirect: e.data.redirect
                ])
                break
        }
    }
}
