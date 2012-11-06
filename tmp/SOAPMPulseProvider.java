package net.playtouch.jaxspot.service.api.billing;

import com.google.common.io.ByteStreams;
import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLDocumentException;
import com.mycila.xmltool.XMLTag;
import com.ovea.json.JSONObject;
import com.ovea.tadjin.util.properties.PropertySettings;
import net.playtouch.jaxspot.model.Partner;
import net.playtouch.jaxspot.model.SubscriptionState;
import org.apache.commons.codec.binary.Base64;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author David Avenante
 */
//TODO: MATHIEU: Connection pooling
public class SOAPMPulseProvider implements MPulseProvider {

    public static final String STATUS = "status";
    public static final String SUBSCRIPTION_ID = "subscriptionId";
    public static final String MESSAGE = "message";
    public static final String SERVICE_URL = "serviceUrl";
    public static final String UNSUBSCRIBE_URL = "unSubscribeUrl";
    private static final Logger LOGGER = Logger.getLogger("MPULSE");

    @Inject
    PropertySettings settings;

    public JSONObject serviceUrl(String clientIp, Partner partner, String reference) {
        String data = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:wap=\"http://billing.mpulse.eu/france/wapbilling\">" +
            "        <soapenv:Header/>" +
            "         <soapenv:Body>" +
            "           <wap:startSubscriptionExtended>" +
            "             <name>" + settings.getString("mpulse.product") + "</name>" +
            "             <ipAddress>" + clientIp + "</ipAddress>" +
            "             <redirectUrl>" + settings.getString("mpulse.callback") + "?provider=mpulse</redirectUrl>" +
            "             <alias></alias>" +
            "             <operator>" + partner.code() + "</operator>" +
            "             <reference>" + reference +  "</reference>" +
            "          </wap:startSubscriptionExtended>" +
            "        </soapenv:Body>" +
            "      </soapenv:Envelope>";

        JSONObject result = new JSONObject();
        try {
            data = new String(send(clientIp, data.getBytes()));
            XMLTag root = XMLDoc.from(data, true).deletePrefixes();
            if (root.hasTag("Body/Fault/faultstring")) {
                result.put(STATUS, "error");
                result.put(MESSAGE, root.getText("Body/Fault/faultstring"));
            } else {
                result.put(STATUS, "success");
                result.put(SUBSCRIPTION_ID, root.getText("Body/startSubscriptionExtendedResponse/return/id"));
                result.put(SERVICE_URL, root.getText("Body/startSubscriptionExtendedResponse/return/redirectUrl"));
            }
        } catch (XMLDocumentException | IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to get service url from mpulse for client " + clientIp + " : " + e.getMessage() + ". DATA =\n" + data, e);
            result.put(STATUS, "error");
            result.put(MESSAGE, "provider_error");
        }
        return result;
    }

    @Override
    public void waitFor(final String subscriptionId, SubscriptionState expected, final int retries, final long waitTIme, final TimeUnit unit) throws TimeoutException, InterruptedException {
        for (int i = 0; i < retries; i++) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Try to reach MPULSE with subscriptionId=" + subscriptionId + " : retry=" + i);
            }
            Thread.sleep(unit.toMillis(waitTIme));
            SubscriptionState current = subscriptionState(subscriptionId);
            if (current == expected) {
                return;
            }
        }
        throw new TimeoutException("Unable to get expected status (" + expected + ") from MPULSE with subscriptionId=" + subscriptionId);
    }

    public SubscriptionState subscriptionState(String subscriptionId) {
        if (subscriptionId == null) {
            throw new IllegalStateException("SubscriptionIs cannot be null");
        }

        String data = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:wap=\"http://billing.mpulse.eu/france/wapbilling\">" +
            "        <soapenv:Header/>" +
            "        <soapenv:Body>" +
            "              <wap:getSubscriptionStatus>" +
            "                <subscriptionId>" + subscriptionId + "</subscriptionId>" +
            "             </wap:getSubscriptionStatus>" +
            "        </soapenv:Body>" +
            "       </soapenv:Envelope>";

        try {
            data = new String(send(subscriptionId, data.getBytes()));
            XMLTag root = XMLDoc.from(data, true).deletePrefixes();
            if (root.hasTag("Body/Fault/faultstring")) {
                return SubscriptionState.PENDING;
            } else {
                String subscriptionStatus = root.getText("Body/getSubscriptionStatusResponse/return/status");
                switch (subscriptionStatus.toUpperCase()) {
                    case "ACTIVE": {
                        return SubscriptionState.ACTIVE;
                    }
                    case "CANCEL":
                    case "STOPPED": {
                        return SubscriptionState.CANCELED;
                    }
                    case "PENDING": {
                        return SubscriptionState.PENDING;
                    }
                    default: {
                        LOGGER.log(Level.SEVERE, "Unknown subscription status from mpulse for subscriptionId " + subscriptionId + " : " + subscriptionStatus);
                        return SubscriptionState.UNKNOWN;
                    }
                }
            }
        } catch (XMLDocumentException | IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to get subscription status from mpulse for subscriptionId " + subscriptionId + " : " + e.getMessage() + ". DATA =\n" + data, e);
            return SubscriptionState.UNKNOWN;
        }
    }

    public JSONObject cancelSubscription(String subscriptionId) {
        String data = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:wap=\"http://billing.mpulse.eu/france/wapbilling\">" +
            "        <soapenv:Header/>" +
            "        <soapenv:Body>" +
            "              <wap:cancelSubscription>" +
            "                <subscriptionId>" + subscriptionId + "</subscriptionId>" +
            "             </wap:cancelSubscription>" +
            "        </soapenv:Body>" +
            "       </soapenv:Envelope>";

        JSONObject result = new JSONObject();
        try {
            data = new String(send(subscriptionId, data.getBytes()));
            XMLTag root = XMLDoc.from(data, true).deletePrefixes();
            if (root.hasTag("Body/Fault/faultstring")) {
                String error = root.getText("Body/Fault/faultstring");
                result.put(STATUS, "error");
                LOGGER.log(Level.SEVERE, "MPulse billing service cancel subscription error (subscriptionId: " + subscriptionId + "): " + error);
            } else {
                result.put(STATUS, "success");
                if (root.hasTag("Body/cancelSubscriptionResponse/return/redirectUrl")) {
                    result.put(UNSUBSCRIBE_URL, root.getText("Body/cancelSubscriptionResponse/return/redirectUrl"));
                }
            }
        } catch (XMLDocumentException | IOException e) {
            result.put(STATUS, "error");
            LOGGER.log(Level.SEVERE, "Unable to cancel subscription for MPulse for subscriptionId " + subscriptionId + " : " + e.getMessage() + ". DATA =\n" + data, e);
        }
        return result;
    }

    @Override
    public JSONObject activeSubscriptions() {
//        http://gateway.mpulse.eu/wapbilling/france/service/active?sn=JAXSPOT&usrn=playtouch&usrp=pl417o!9a
        JSONObject result = new JSONObject();
        try {
            String query = String.format("sn=%s&usrn=%s&usrp=%s",
                    settings.getString("mpulse.product"),
                    URLEncoder.encode(settings.getString("mpulse.username"), "UTF-8"),
                    URLEncoder.encode(settings.getString("mpulse.password"), "UTF-8"));

            HttpURLConnection conn = (HttpURLConnection) new URL(settings.getString("mpulse.active") + "?" + query).openConnection();
            conn.setRequestProperty("Accept-Charset", "UTF-8");
            conn.setRequestMethod("GET");


        } catch (XMLDocumentException | IOException e) {

        }


        return result;
    }

    private byte[] send(String id, byte[] data) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(settings.getString("mpulse.gateway")).openConnection();
        try {
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(10000);

            conn.setRequestProperty("SOAPAction", "");
            conn.setRequestProperty("Authorization", "Basic " + Base64.encodeBase64String((settings.getString("mpulse.username") + ":" + settings.getString("mpulse.password")).getBytes()));
            conn.setRequestProperty("Host", "gateway.mpulse.eu");
            conn.setRequestProperty("Content-Type", "text/xml;charset=UTF-8");

            conn.setRequestProperty("Content-Length", "" + data.length);
            conn.getOutputStream().write(data);
            conn.getOutputStream().flush();

            byte[] dIn = new byte[0], dErr = new byte[0];
            try (InputStream in = conn.getInputStream(); InputStream err = conn.getErrorStream()) {
                if (in != null)
                    dIn = ByteStreams.toByteArray(in);
                if (err != null)
                    dErr = ByteStreams.toByteArray(err);
            }

            LOGGER.log(Level.FINE, "MPULSE response code for " + id + "  : " + conn.getResponseCode() + ", in=" + dIn.length + ", err=" + dErr.length);
            return dIn.length > 0 ? dIn : dErr;
        } finally {
            conn.disconnect();
        }
    }
}
