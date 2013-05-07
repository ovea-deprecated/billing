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

import groovy.json.JsonBuilder

import javax.net.ssl.*
import javax.servlet.http.HttpServletResponse
import javax.xml.ws.WebServiceException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class IO {

    private static final Logger LOGGER = Logger.getLogger(IO.name)

    static void send(HttpServletResponse resp, int status, data = [:]) {
        resp.status = status
        resp.contentType = 'application/json;charset=utf-8'
        if (data) {
            def content = new JsonBuilder(data).toString().getBytes('UTF-8')
            resp.contentLength = content.length
            resp.outputStream << content
        } else {
            resp.contentLength = 0
        }
    }

    static def soapRequest(String url, String request, Map<String, String> headers = [:]) throws WebServiceException {
        HttpURLConnection connection = new URL(url).openConnection() as HttpURLConnection
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsCon = (HttpsURLConnection) connection
            SSLContext sc = SSLContext.getInstance("SSL")
            sc.init(null, [new X509TrustManager() {
                @Override
                X509Certificate[] getAcceptedIssuers() { null }

                @Override
                void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                @Override
                void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }] as TrustManager[], new SecureRandom())
            httpsCon.SSLSocketFactory = sc.socketFactory
            httpsCon.hostnameVerifier = ({ true } as HostnameVerifier)
        }
        try {
            def data = request.bytes
            connection.requestMethod = 'POST'
            connection.useCaches = false
            connection.doOutput = true
            connection.doInput = true
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20000
            connection.setRequestProperty('Content-Length', "${data.length}")
            connection.setRequestProperty('Content-Type', "application/soap+xml;charset=UTF-8")
            connection.setRequestProperty("SOAPAction", "")
            headers.each { k, v -> connection.setRequestProperty(k, v) }
            connection.outputStream.bytes = data
            String response = connection.responseCode == 200 ? connection.inputStream.text : connection.errorStream.text
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("${url}:${connection.responseCode}\n${request}\n${response}")
            }
            String[] contentType = (connection.getHeaderField('Content-Type') ?: '').trim().split(';')
            def soapEnv = null
            switch (contentType[0]) {
                case 'application/soap+xml':
                case 'text/xml':
                    soapEnv = new XmlSlurper(false, true).parseText(response)
                    break
                case 'multipart/related':
                    response = response.readLines().find { it.startsWith('<?xml') } ?: { throw new IllegalStateException('Found no XML in repsonse ' + response) }.call()
                    soapEnv = new XmlSlurper(false, true).parseText(response)
                    break
                default:
                    throw new IllegalStateException('Unsupported Content-Type: ' + contentType)
            }
            if (soapEnv.Body.Fault.faultstring as String) {
                throw new WebServiceException(soapEnv.Body.Fault.faultstring as String)
            }
            if (connection.responseCode != 200) {
                throw new WebServiceException(response)
            }
            return soapEnv
        } finally {
            connection.disconnect()
        }
    }

    /*public static void main(String[] args) {
        String req = """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:com="com.bango.webservices.subscriptions">
           <soapenv:Header/>
           <soapenv:Body>
              <com:GetSubscriptionInfo>
                 <com:request>
                    <com:username>USERNAME</com:username>
                    <com:password>PASSWORD</com:password>
                    <com:subscriptionId>SUBID</com:subscriptionId>
                 </com:request>
              </com:GetSubscriptionInfo>
           </soapenv:Body>
        </soapenv:Envelope>"""
        println soapRequest('https://webservices.bango.com/subscriptions/service.asmx', req, [
            Host: 'webservices.bango.com',
            SOAPAction: 'com.bango.webservices.subscriptions/GetSubscriptionInfo',
            'Content-Type': 'text/xml;charset=UTF-8'
        ]).toString()
    }*/
}
