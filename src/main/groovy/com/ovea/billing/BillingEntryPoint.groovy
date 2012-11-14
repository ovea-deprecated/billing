package com.ovea.billing

import com.ovea.tadjin.util.properties.PropertySettings

import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.Filter
import javax.servlet.FilterConfig
import javax.servlet.ServletConfig
import javax.servlet.ServletContext
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST
import static javax.servlet.http.HttpServletResponse.SC_OK

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-06
 */
@Singleton
class BillingEntryPoint extends HttpServlet implements Filter {

    private static final String FILTERED = BillingEntryPoint.name + '.FILTERED'
    private static final Logger LOGGER = Logger.getLogger(BillingEntryPoint.name)

    public static final String CONFIG = 'config'
    public static final String URL = 'url'

    BillingConfig config
    ServletContext servletContext
    Collection<BillingCallback> connectors = []

    @Inject
    BillingCallback callback

    @Inject
    PropertySettings env

    @Override
    void init(ServletConfig config) {
        servletContext = config.servletContext
        super.init(config)
        init(config.getInitParameter(CONFIG), config.getInitParameter(BillingEntryPoint.URL))
    }

    @Override
    void init(FilterConfig filterConfig) {
        servletContext = filterConfig.servletContext
        init(filterConfig.getInitParameter(CONFIG), filterConfig.getInitParameter(BillingEntryPoint.URL))
    }

    void init(String configLocation, String url) {
        LOGGER.fine('init: ' + configLocation)
        config = new BillingConfig(configLocation, env, url)
        connectors = config.connectors
    }

    @Override
    void doFilter(ServletRequest request, ServletResponse response, javax.servlet.FilterChain chain) {
        request.setAttribute(FILTERED, 'true')
        doPost((HttpServletRequest) request, (HttpServletResponse) response)
        chain.doFilter(request, response)
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
            if (event && event.answer) {
                IO.send resp, event.answer.status as int, event.answer.data
            }
        } catch (e) {
            LOGGER.log(Level.SEVERE, e.message, e)
            IO.send resp, SC_BAD_REQUEST, [message: e.message]
        }
    }

    BillingEvent handleCallback(String path, HttpServletRequest request, HttpServletResponse response) {
        BillingEvent event = new BillingEvent(
            type: BillingEventType.CALLBACK,
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

    BillingEvent cancel(String product, HttpServletRequest request, HttpServletResponse response) {
        if (config.canCancel(product)) {
            def platforms = config.platforms(product)
            BillingEvent e = new BillingEvent(
                type: BillingEventType.CANCEL_REQUESTED,
                product: product,
                request: request,
                response: response
            )
            if (platforms.size() == 1) {
                e.platform = platforms[0] as BillingPlatform
            }
            connectors*.onEvent(e)
            if (!e.prevented) {
                callback.onEvent(e)
                if (!e.prevented) {
                    e.type = BillingEventType.CANCEL_ACCEPTED
                    connectors*.onEvent(e)
                }
            }
            return e
        } else {
            response.status = SC_BAD_REQUEST
            return null
        }
    }

    BillingEvent buy(String product, BillingPlatform platform, HttpServletRequest req, HttpServletResponse resp) {
        if (config.canPay(product, platform)) {
            BillingEvent e = new BillingEvent(
                type: BillingEventType.BUY_REQUESTED,
                product: product,
                platform: BillingPlatform.mpulse,
                request: req,
                response: resp
            )
            connectors*.onEvent(e)
            if (!e.prevented) {
                callback.onEvent(e)
                if (!e.prevented) {
                    e.type = BillingEventType.BUY_ACCEPTED
                    connectors*.onEvent(e)
                    if (!e.prevented) {
                        callback.onEvent(e)
                    }
                }
            }
            return e
        } else {
            resp.status = SC_BAD_REQUEST
            return null
        }
    }

}
