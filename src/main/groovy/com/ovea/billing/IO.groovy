package com.ovea.billing

import groovy.json.JsonBuilder

import java.util.logging.Level
import java.util.logging.Logger
import javax.servlet.http.HttpServletResponse
import javax.xml.ws.WebServiceException

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class IO {

    private static final Logger LOGGER = Logger.getLogger(IO.name)

    static void send(HttpServletResponse resp, int status, data) {
        def content = new JsonBuilder(data).toString().getBytes('UTF-8')
        resp.status = status
        resp.contentLength = content.length
        resp.contentType = 'application/json;charset=utf-8'
        resp.outputStream << content
    }

    static def soapRequest(String url, String request, Map<String, String> headers = [:]) throws WebServiceException {
        HttpURLConnection connection = new URL(url).openConnection() as HttpURLConnection
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
            headers.each {k, v -> connection.setRequestProperty(k, v)}
            connection.outputStream.bytes = data
            String response = connection.responseCode == 200 ? connection.inputStream.text : connection.errorStream.text
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("${url}:${connection.responseCode}\n${request}\n${response}")
            }
            String[] contentType = (connection.getHeaderField('Content-Type') ?: '').trim().split(';')
            def soapEnv = null
            switch (contentType[0]) {
                case 'application/soap+xml':
                    soapEnv = new XmlSlurper(false, true).parseText(response)
                    break
                case 'multipart/related':
                    response = response.readLines().find {it.startsWith('<?xml')} ?: {throw new IllegalStateException('Found no XML in repsonse ' + response)}.call()
                    soapEnv = new XmlSlurper(false, true).parseText(response)
                    break
                default:
                    throw new IllegalStateException('Unsupported Content-Type: ' + connection.getHeaderField('Content-Type'))
            }
            if (soapEnv?.Body?.Fault) {
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

}
