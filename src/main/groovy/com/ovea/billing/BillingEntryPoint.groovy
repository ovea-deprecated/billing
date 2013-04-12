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
package com.ovea.billing

import com.ovea.tadjin.util.properties.PropertySettings

import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.ServletConfig
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.logging.Level
import java.util.logging.Logger

import static javax.servlet.http.HttpServletResponse.*

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-06
 */
@Singleton
class BillingEntryPoint extends HttpServlet implements BillingService {

    private static final Logger LOGGER = Logger.getLogger(BillingEntryPoint.name)

    public static final String CONFIG = 'config'
    public static final String URL = 'url'

    @Inject
    BillingCallback callback

    @Inject
    PropertySettings env

    private BillingConfig config
    private Collection<BillingCallback> connectors = []

    @Override
    void init(ServletConfig config) {
        super.init(config)
        String configLocation = config.getInitParameter(CONFIG)
        String url = config.getInitParameter(BillingEntryPoint.URL)
        LOGGER.fine('init... ' + configLocation)
        this.config = new BillingConfig(configLocation, env, url)
        this.connectors = this.config.connectors
        this.config.scheduler.start()
    }

    @Override
    void destroy() {
        LOGGER.fine('shutdown...')
        this.config.scheduler.shutdown()
        super.destroy()
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        doPost(req, resp)
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            String path = req.requestURI.substring(req.servletPath.length())
            BillingEvent event = null
            if (req.getParameter('action')) {
                switch (req.getParameter('action')) {
                    case 'options':
                        IO.send resp, SC_OK, [
                            products: config.productIds,
                            platforms: config.platformIds
                        ]
                        break
                    case 'buy':
                        String product = req.getParameter('product')
                        BillingPlatform platform = req.getParameter('platform') as BillingPlatform
                        event = buy(product, platform, req, resp)
                        break
                    case 'cancel':
                        String product = req.getParameter('product')
                        event = cancel(product, req, resp)
                        break
                }
            } else if (path.startsWith('/callback/')) {
                event = handleCallback(path, req, resp)
            } else if (path.startsWith('/event/')) {
                event = handleEvent(path, req, resp)
            }
            if (!resp.committed) {
                if (event && event.answer) {
                    IO.send resp, event.answer.status as int, event.answer.data
                } else {
                    IO.send resp, SC_NO_CONTENT
                }
            } else {
                LOGGER.log(Level.FINEST, 'Response already commited with code: ' + resp.status)
            }
        } catch (e) {
            LOGGER.log(Level.SEVERE, e.message, e)
            IO.send resp, SC_BAD_REQUEST, [message: e.message]
        }
    }

    BillingEvent handleCallback(String path, HttpServletRequest request, HttpServletResponse response) {
        BillingEvent event = new BillingEvent(
            type: BillingEventType.CALLBACK_REQUEST,
            platform: path.substring(10) as BillingPlatform,
            request: request,
            response: response
        )
        connectors*.onEvent(event)
        if (!event.prevented) {
            callback.onEvent(event)
        }
        return event
    }

    BillingEvent handleEvent(String path, HttpServletRequest request, HttpServletResponse response) {
        BillingEvent event = new BillingEvent(
            type: BillingEventType.NOTIFICATION,
            platform: path.substring(7) as BillingPlatform,
            request: request,
            response: response
        )
        // let connectors parse events and construct a batch list
        connectors*.onEvent(event)
        if (!event.prevented) {
            if (event.childs) {
                // if batches, reprocess all childs normally
                event.childs.each { BillingEvent child ->
                    connectors*.onEvent(child)
                    if (!child.prevented) {
                        callback.onEvent(child)
                    }
                }
            } else {
                // if not batches, normal behavior
                callback.onEvent(event)
            }
        }
        return event
    }

    BillingEvent cancel(String product, HttpServletRequest request, HttpServletResponse response) {
        if (config.canCancel(product)) {
            BillingEvent e = new BillingEvent(
                type: BillingEventType.CANCEL_REQUESTED,
                product: product,
                request: request,
                response: response
            )
            connectors*.onEvent(e)
            if (!e.prevented) {
                callback.onEvent(e)
                if (!e.prevented) {
                    e.type = BillingEventType.CANCEL_REQUEST_ACCEPTED
                    connectors*.onEvent(e)
                    if (!e.prevented) {
                        callback.onEvent(e)
                    }
                }
            }
            return e
        }
        throw new IllegalArgumentException('Cannot cancel product ' + product)
    }

    BillingEvent buy(String product, BillingPlatform platform, HttpServletRequest req, HttpServletResponse resp) {
        if (config.canPay(product, platform)) {
            BillingEvent e = new BillingEvent(
                type: BillingEventType.BUY_REQUESTED,
                product: product,
                platform: platform,
                request: req,
                response: resp
            )
            connectors*.onEvent(e)
            if (!e.prevented) {
                callback.onEvent(e)
                if (!e.prevented) {
                    e.type = BillingEventType.BUY_REQUEST_ACCEPTED
                    connectors*.onEvent(e)
                    if (!e.prevented) {
                        callback.onEvent(e)
                    }
                }
            }
            return e
        }
        throw new IllegalArgumentException('Cannot buy product ' + product)
    }

    @Override
    void fireEvent(BillingEvent e) {
        connectors*.onEvent(e)
        if (!e.prevented) {
            callback.onEvent(e)
        }
    }
}
