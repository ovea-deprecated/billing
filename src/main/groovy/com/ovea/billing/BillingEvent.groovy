package com.ovea.billing

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class BillingEvent {

    String message = ''
    def data = [:]
    boolean prevented

    BillingEventType type
    BillingPlatform platform
    String product
    HttpServletRequest request
    HttpServletResponse response

    def answer = [:]

    void prevent(String message = '') {
        prevented = true
        this.message = message
        IO.send response, SC_BAD_REQUEST, [message: message]
    }

    void answer(data) {
        answer(HttpServletResponse.SC_OK, data)
    }

    void answer(int status, data) {
        answer = [
            status: status,
            data: data
        ]
    }
}
