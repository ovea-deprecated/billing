package com.ovea.billing

import javax.inject.Singleton
import javax.servlet.Filter
import javax.servlet.FilterConfig
import javax.servlet.ServletConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServlet

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-06
 */
@Singleton
class BillingEntryPoint extends HttpServlet implements Filter {

    public static final String CONFIG = 'config'

    @Override
    void init(ServletConfig config) {

    }

    @Override
    void init(FilterConfig filterConfig) {

    }

    @Override
    void doFilter(ServletRequest request, ServletResponse response, javax.servlet.FilterChain chain) {

    }
}
