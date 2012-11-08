package net.playtouch.jaxspot.service.api.job;

import com.mycila.inject.schedule.Cron;
import com.mycila.jdbc.UnitOfWork;
import net.playtouch.jaxspot.model.Member;
import net.playtouch.jaxspot.model.Subscription;
import net.playtouch.jaxspot.model.SubscriptionState;
import net.playtouch.jaxspot.repository.SubscriptionRepository;
import net.playtouch.jaxspot.service.api.billing.MPulseProvider;
import net.playtouch.jaxspot.service.api.mail.MailService;
import org.joda.time.DateTime;

import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.playtouch.jaxspot.model.SubscriptionProvider.MPULSE;
import static net.playtouch.jaxspot.model.SubscriptionState.ACTIVE;

/**
 * @author David Avenante
 */
@Cron("0 */30 * * * ? *")
public class MPulseSubscriptionRecoveryJob implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(MPulseSubscriptionRecoveryJob.class.getName());

    @Inject
    MPulseProvider mpulseProvider;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    MailService mailService;

    @Inject
    UnitOfWork unitOfWork;

    @Override
    public void run() {
        unitOfWork.run(new Runnable() {
            @Override
            public void run() {
                for (Subscription subscription : subscriptionRepository.getPendingSubscriptionFor(MPULSE)) {
                    SubscriptionState mpulseStatus = mpulseProvider.subscriptionState(subscription.code());
                    if (mpulseStatus == ACTIVE) {
                        subscription.activate(new DateTime().plusDays(8));
                        subscriptionRepository.update(subscription);
                        Member member = subscriptionRepository.getMemberFor(subscription);
                        LOGGER.log(Level.INFO, subscription + " regenerated for member " + member.email());
                        mailService.sendSubscriptionRecoveryEmailTo(member);
                    } else {
                        // Delete subscription when state included in the
                    }
                }
            }
        });
    }
}
