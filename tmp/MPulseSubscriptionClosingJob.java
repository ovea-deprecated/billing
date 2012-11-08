package net.playtouch.jaxspot.service.api.job;

import com.mycila.inject.schedule.Cron;
import com.mycila.jdbc.UnitOfWork;
import net.playtouch.jaxspot.model.Subscription;
import net.playtouch.jaxspot.repository.SubscriptionRepository;
import org.joda.time.DateTime;

import javax.inject.Inject;
import java.util.logging.Logger;

/**
 * @author David Avenante
 */
@Cron("0 0 1 * * ? *")
public class MPulseSubscriptionClosingJob implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(MPulseSubscriptionClosingJob.class.getName());

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    UnitOfWork unitOfWork;

    @Override
    public void run() {
        unitOfWork.run(new Runnable() {
            @Override
            public void run() {
                for (Subscription subscription : subscriptionRepository.nonRenewableExpiredAt(new DateTime())) {
                    subscription.finish();
                    LOGGER.info("Ending " + subscription);
                    subscriptionRepository.update(subscription);
                }
            }
        });
    }
}
