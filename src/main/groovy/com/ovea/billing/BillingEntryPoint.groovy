package com.ovea.billing

import com.ovea.tadjin.util.Resource
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

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

    public static final String CONFIG = 'config'

    def config
    def ServletContext servletContext

    @Override
    void init(ServletConfig config) {
        servletContext = config.servletContext
        super.init(config)
        init(config.getInitParameter(CONFIG))
    }

    @Override
    void init(FilterConfig filterConfig) {
        servletContext = filterConfig.servletContext
        init(filterConfig.getInitParameter(CONFIG))
    }

    void init(String configLocation) {
        Resource r = Resource.from(configLocation)
        if (!r.exist()) {
            throw new IllegalArgumentException('Missing InitParameter: ' + CONFIG)
        }
        config = new JsonSlurper().parseText(r.readAsString())
    }

    @Override
    void doFilter(ServletRequest request, ServletResponse response, javax.servlet.FilterChain chain) {
        doPost((HttpServletRequest) request, (HttpServletResponse) response)
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {

        switch (req.getParameter('action')) {
            case 'options':
                send resp, SC_OK, [
                    products: config.products.keySet(),
                    platforms: config.platforms.keySet(),
                ]
                break
            case 'buy':
                break
            case 'cancel':
                break
            default:
                resp.status = SC_BAD_REQUEST
        }
    }

    void send(HttpServletResponse resp, int status, data) {
        def content = new JsonBuilder(data).toString().getBytes('UTF-8')
        resp.status = status
        resp.contentLength = content.length
        resp.contentType = 'application/json;charset=utf-8'
        resp.outputStream << content
    }
}
