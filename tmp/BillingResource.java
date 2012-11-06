package net.playtouch.jaxspot.service.api.billing;

import com.ovea.i18n.I18NService;
import com.ovea.i18n.Message;
import com.ovea.json.JSONArray;
import com.ovea.json.JSONObject;
import com.ovea.tadjin.util.properties.PropertySettings;
import net.playtouch.jaxspot.model.Member;
import net.playtouch.jaxspot.model.Subscription;
import net.playtouch.jaxspot.model.SubscriptionProvider;
import net.playtouch.jaxspot.model.SubscriptionState;
import net.playtouch.jaxspot.repository.InexistingMemberException;
import net.playtouch.jaxspot.repository.InexistingSubscriptionException;
import net.playtouch.jaxspot.repository.MemberRepository;
import net.playtouch.jaxspot.repository.SubscriptionException;
import net.playtouch.jaxspot.repository.SubscriptionRepository;
import net.playtouch.jaxspot.security.auth.Role;
import net.playtouch.jaxspot.security.auth.Security;
import net.playtouch.jaxspot.service.api.Redirect;
import net.playtouch.jaxspot.service.api.security.SessionManager;
import org.joda.time.DateTime;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static net.playtouch.jaxspot.model.SubscriptionProvider.FACEBOOK;
import static net.playtouch.jaxspot.model.SubscriptionProvider.MPULSE;
import static net.playtouch.jaxspot.model.SubscriptionState.ACTIVE;
import static net.playtouch.jaxspot.model.SubscriptionState.PENDING;

/**
 * @author David Avenante
 */
@Path("/billing")
@Produces(MediaType.APPLICATION_JSON)
public class BillingResource {

    private static final Logger LOGGER = Logger.getLogger("SUBSCRIPTIONS");

    public static final String CALLBACK_URL = "redirectURL";

    @Inject
    MPulseProvider mpulseProvider;

    @Inject
    Provider<Member> currentMember;

    @Inject
    Provider<HttpServletRequest> request;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    MemberRepository memberRepository;

    @Inject
    SessionManager sessionManager;

    @Inject
    Redirect redirect;

    @Inject
    PropertySettings settings;

    @Inject
    SubscriberPartnerCallback subscriberPartnerCallback;

    @Message
    I18NService messages;

    public static final String MESSAGE = "message";
    public static final String SUBSCRIPTION_ID = "subscriptionId";
    public static final String SERVICE_URL = "serviceUrl";
    public static final String STATUS = "status";
    public static final String UNSUBSCRIBE_URL = "unSubscribeUrl";

    @GET
    @Path("/subscription/{provider}/service")
    @RolesAllowed(Role.MEMBER)
    public Response redirectToBillingProviderService(@PathParam("provider") SubscriptionProvider provider) {
        Member member = currentMember.get();
        try {
            // first try to match a subscriber
            subscriptionRepository.getActiveSubscriptionFor(member);
            if (LOGGER.isLoggable(Level.INFO))
                LOGGER.log(Level.INFO, "Member : " + member.email() + " try a new subscription but have an already active!!!");
            return Response.status(BAD_REQUEST).entity(new JSONObject().put(MESSAGE, "already_subscriber")).build();
        } catch (InexistingSubscriptionException e) {
            // if not existing check
            if (provider == MPULSE) {
                JSONObject result = mpulseProvider.serviceUrl(request.get().getRemoteAddr(), member.referrer(), member.id());
                if (result.getString(STATUS).equals("success")) {
                    subscriptionRepository.create(Subscription.forProviderWithCode(MPULSE, result.getString(SUBSCRIPTION_ID)).isRenewable(true), currentMember.get());
                    if (LOGGER.isLoggable(Level.INFO))
                        LOGGER.log(Level.INFO, "Member : " + member.email() + " create a new subscription with code " + result.getString(SUBSCRIPTION_ID));
                    return Response.ok().entity(new JSONObject().put(CALLBACK_URL, result.getString(SERVICE_URL))).build();
                } else {
                    return Response.status(BAD_REQUEST).entity(new JSONObject().put(MESSAGE, result.getString(MESSAGE))).build();
                }
            }
            return Response.status(BAD_REQUEST).entity(new JSONObject().put(MESSAGE, "unknown_provider")).build();
        }
    }

    @GET
    @Path("/subscription/{provider}/cancel")
    @RolesAllowed(Role.SUBSCRIBER)
    public Response unSubscribe(@PathParam("provider") SubscriptionProvider provider) {
        Member member = currentMember.get();
        if (provider == MPULSE) {
            try {
                Subscription subscription = subscriptionRepository.getActiveSubscriptionFor(member);
                SubscriptionState state = mpulseProvider.subscriptionState(subscription.code());
                if (state == ACTIVE) {
                    JSONObject result = mpulseProvider.cancelSubscription(subscription.code());
                    if (result.getString(STATUS).equals("success")) {
                        if (result.isNull(UNSUBSCRIBE_URL)) {
                            subscriptionRepository.update(subscription.cancel());
                        } else {
                            request.get().getSession().setAttribute("start-cancellation", true);
                        }
                        if (LOGGER.isLoggable(Level.INFO))
                            LOGGER.log(Level.INFO, "Member : " + member.email() + " cancel a subscription with code " + subscription.code());
                        return Response.ok().entity(result).build();
                    }
                } else {
                    subscriptionRepository.update(subscription.cancel());
                    if (LOGGER.isLoggable(Level.INFO))
                        LOGGER.log(Level.INFO, "Member : " + member.email() + " has already cancelled the subscription with code " + subscription.code());
                    return Response.ok().build();
                }
            } catch (SubscriptionException e) {
                LOGGER.log(Level.INFO, "Member " + member.email() + " cannot unsubscribe", e);
            }
        }
        return Response.status(BAD_REQUEST).build();
    }

    @GET
    @Path("/subscription/callback")
    @PermitAll
    public Response billingCallback() {
        Member member = currentMember.get();
        SubscriptionProvider provider = SubscriptionProvider.fromName(request.get().getParameter("provider"));

        if (provider == MPULSE) {
            String code = request.get().getParameter("tid");
            SubscriptionState state = mpulseProvider.subscriptionState(code);
            if (state == ACTIVE) {
                Subscription subscription;
                try {
                    subscription = subscriptionRepository.findByCode(code);
                } catch (SubscriptionException e) {
                    if (LOGGER.isLoggable(Level.INFO))
                        LOGGER.log(Level.INFO, "Member : " + member.email() + " has no active subscription in our DB (" + code + ") => create new", e);
                    subscription = Subscription.forProviderWithCode(MPULSE, code).isRenewable(true);
                    subscriptionRepository.create(subscription, member);
                }

                if (member.isAnonymous() && subscription.status() == PENDING) {
                    member = subscriptionRepository.getMemberFor(subscription);
                    Security.loginByEmail(member.email());
                    LOGGER.log(Level.SEVERE, "Force login for member " + member.email() + " after MPULSE subscription callback for subscription code " + subscription.code());
                }

                subscription.activate(new DateTime().plusDays(8));
                subscriptionRepository.update(subscription);
                sessionManager.refreshRoles(member);
                subscriberPartnerCallback.notify(member);
                if (LOGGER.isLoggable(Level.INFO))
                    LOGGER.log(Level.INFO, "Member : " + member.email() + " complete successfully subscription with code : " + code);
            }
        }
        return redirect.toRoot();
    }

    @POST
    @Path("/facebook")
    @PermitAll
    public Response facebookCreditCallback() throws UnsupportedEncodingException {
        // TODO add signature verification
        /*String signedRequest = request.get().getParameter("signed_request");
        if (signedRequest == null || signedRequest.length() == 0) {
            LOGGER.log(Level.SEVERE, "Facebook signed request is missing");
        }
        StringTokenizer request_token = new StringTokenizer(signedRequest, ".");
        String signature = request_token.nextToken();
        String data = new String(Base64.decodeBase64(request_token.nextToken()), "UTF8");
        JSONObject payload = new JSONObject(data);*/

        String method = request.get().getParameter("method");
        switch (method) {
            case "payments_get_items": {
                try {
                    Member buyer = memberRepository.findByFacebookId(Long.valueOf(request.get().getParameter("buyer")));
                    return Response.ok(item(request.get().getParameter("order_info"), buyer.locale())).build();
                } catch (InexistingMemberException | NumberFormatException e) {
                    LOGGER.log(Level.WARNING, "Unable to process method 'payments_get_items' for buyer " + request.get().getParameter("buyer"), e);
                    return Response.ok().build();
                }
            }
            case "payments_status_update": {
                String status = request.get().getParameter("status");
                JSONObject order_details = new JSONObject(request.get().getParameter("order_details"));
                Member buyer = memberRepository.findByFacebookId(order_details.getLong("buyer"));

                String orderId = request.get().getParameter("order_id");
                Subscription subscription = Subscription.forProviderWithCode(FACEBOOK, orderId);
                switch (status) {
                    case "placed":
                        subscriptionRepository.create(subscription, buyer);
                        return Response.ok(payload(orderId)).build();
                    case "settled":
                        subscription = subscriptionRepository.findByCode(orderId);
                        subscription.activate(new DateTime().plusMonths(4));
                        subscriptionRepository.update(subscription);
                        subscriberPartnerCallback.notify(buyer);
                        return Response.ok().build();
                }
            }
            default: {
                LOGGER.log(Level.WARNING, "Not supported facebook callback method: " + method);
                return Response.ok().build();
            }
        }
    }

    private JSONObject payload(String orderId) {
        JSONObject payload = new JSONObject();
        JSONObject payload_detail = new JSONObject();
        payload_detail.put("order_id", orderId);
        payload_detail.put("status", "settled");

        payload.put("content", payload_detail);
        payload.put("method", "payments_status_update");

        return payload;
    }

    private JSONObject item(String packageName, Locale locale) {
        JSONObject item = new JSONObject();
        JSONObject item_detail = new JSONObject();

        item_detail.put("title", messages.forLocale(locale).message("facebook.item.subscription.1_week.title"));
        item_detail.put("description", messages.forLocale(locale).message("facebook.item.subscription.1_week.description"));
        item_detail.put("price", messages.forLocale(locale).message("facebook.item.subscription.1_week.price"));
        item_detail.put("image_url", settings.getString("ws.resource") + messages.forLocale(locale).message("facebook.item.subscription.1_week.image_url"));
        item_detail.put("product_url", settings.getString("ws.resource") + messages.forLocale(locale).message("facebook.item.subscription.1_week.product_url"));

        item.put("content", new JSONArray().put(item_detail));
        item.put("method", "payments_get_items");

        return item;
    }

}
