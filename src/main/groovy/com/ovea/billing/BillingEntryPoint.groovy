package com.ovea.billing

import com.ovea.tadjin.util.properties.PropertySettings

import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.ServletConfig
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT
import static javax.servlet.http.HttpServletResponse.SC_OK

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
            platforms: [path.substring(10) as BillingPlatform],
            request: request,
            response: response
        )
        connectors*.onEvent(event)
        if (!event.prevented) {
            callback.onEvent(event)
        }
        return event
    }

    BillingEvent cancel(String product, HttpServletRequest request, HttpServletResponse response) {
        if (config.canCancel(product)) {
            BillingEvent e = new BillingEvent(
                type: BillingEventType.CANCEL_REQUESTED,
                product: product,
                request: request,
                response: response,
                platforms: config.platforms(product)
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
                platforms: [platform],
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
