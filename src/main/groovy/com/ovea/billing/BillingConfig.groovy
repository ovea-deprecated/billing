package com.ovea.billing

import com.ovea.billing.support.FacebookConnector
import com.ovea.billing.support.MPulseConnector
import com.ovea.billing.support.PayPalConnector
import com.ovea.tadjin.util.Resource
import com.ovea.tadjin.util.properties.PropertySettings
import groovy.json.JsonSlurper

import java.util.logging.Logger

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class BillingConfig {

    private static final Logger LOGGER = Logger.getLogger(BillingConfig.name)

    PropertySettings env
    def json
    String url

    BillingConfig(String configLocation, PropertySettings env, String url) throws IllegalBillingConfigException {
        this.env = env
        this.url = url
        Resource r = Resource.from(configLocation)
        if (!r.exist()) {
            throw new IllegalArgumentException('Missing configLocation: ' + configLocation)
        }
        this.json = new JsonSlurper().parseText(r.readAsString())
        if (!productIds) {
            LOGGER.warning('No products defines in ' + configLocation)
        }
        if (!platformIds) {
            LOGGER.warning('No billing platforms defines in ' + configLocation)
        }
        platformIds.findAll {!BillingPlatform.from(it)}.with {if (it) throw new IllegalBillingConfigException('Unsupported platforms: ' + it); it}
        json.products.each {p, o ->
            (o.platforms ?: [:]).keySet().findAll {!(it in platformIds)}.with {if (it) throw new IllegalBillingConfigException('Unsupported platforms: ' + it + ' for product ' + p); it}
        }
    }

    Collection<String> getProductIds() {
        return json.products?.keySet()
    }

    Collection<String> getPlatformIds() {
        return json.platforms?.keySet()
    }

    boolean supportPlatform(BillingPlatform platform) {
        return platform.name() in platformIds
    }

    boolean supportProduct(String product) {
        return product in productIds
    }

    boolean canPay(String product, BillingPlatform platform) {
        return supportPlatform(platform) && supportProduct(product) && (json.products[product].platforms ?: [:]).containsKey(platform)
    }

    boolean canCancel(String product) {
        return json.products[product].cancellable
    }

    Collection<BillingCallback> getConnectors() {
        return BillingPlatform.values().findAll {BillingPlatform e -> e.name() in platformIds}.collect {
            switch (it) {
                case BillingPlatform.facebook: return new FacebookConnector(this)
                case BillingPlatform.mpulse: return new MPulseConnector(this)
                case BillingPlatform.paypal: return new PayPalConnector(this)
            }
            throw new AssertionError()
        }
    }

    Collection<BillingPlatform> platforms(String product) {
        return (json.products[product].platforms ?: [:]).keySet().collect {it as BillingPlatform}
    }

}
