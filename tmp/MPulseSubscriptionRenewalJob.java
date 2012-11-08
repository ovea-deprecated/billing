package net.playtouch.jaxspot.service.api.job;

import com.mycila.inject.schedule.Cron;
import com.mycila.jdbc.UnitOfWork;
import net.playtouch.jaxspot.model.Subscription;
import net.playtouch.jaxspot.model.SubscriptionState;
import net.playtouch.jaxspot.repository.SubscriptionRepository;
import net.playtouch.jaxspot.service.api.billing.MPulseProvider;
import org.joda.time.DateTime;

import javax.inject.Inject;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.playtouch.jaxspot.model.SubscriptionState.ACTIVE;
import static net.playtouch.jaxspot.model.SubscriptionState.CANCELED;

/**
 * @author David Avenante
 */
@Cron("0 0 0 * * ? *")
public class MPulseSubscriptionRenewalJob implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(MPulseSubscriptionRenewalJob.class.getName());

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    UnitOfWork unitOfWork;

    @Inject
    MPulseProvider mpulseProvider;

    @Override
    public void run() {
        unitOfWork.run(new Runnable() {
            @Override
            public void run() {
                List<Subscription> subscriptions = subscriptionRepository.renewableExpiredAt(new DateTime());
                for (Subscription subscription : subscriptions) {
                    SubscriptionState state = mpulseProvider.subscriptionState(subscription.code());
                    if (state == ACTIVE) {
                        subscription.extendSubscription(8);
                    }
                    if (state == CANCELED) {
                        subscription.finish();
                    }
                    LOGGER.log(Level.INFO, "Renewall to " + subscription);
                    subscriptionRepository.update(subscription);
                }
            }
        });
    }
}
