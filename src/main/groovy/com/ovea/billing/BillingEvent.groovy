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

import groovy.transform.ToString

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
@ToString(excludes = ['request', 'response'], includeNames = true, ignoreNulls = true)
class BillingEvent {

    List<BillingEvent> childs = []

    def data = [:]
    boolean prevented

    BillingEventType type
    BillingPlatform platform
    String product
    HttpServletRequest request
    HttpServletResponse response

    def answer = [:]

    BillingEvent newChild(BillingEventType type) {
        BillingEvent be = new BillingEvent(
            type: type,
            platform: platform,
            request: request,
            response: response,
            product: product
        )
        childs << be
        return be
    }

    void prevent(String message = '') {
        prevented = true
        answer SC_BAD_REQUEST, [message: message]
    }

    void answer(data) {
        answer(HttpServletResponse.SC_OK, data)
    }

    void answer(int status, data = [:]) {
        answer = [
            status: status,
            data: data
        ]
    }
}
