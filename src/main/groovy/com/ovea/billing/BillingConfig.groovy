package com.ovea.billing

import com.ovea.billing.support.FacebookConnector
import com.ovea.billing.support.MPulseConnector
import com.ovea.billing.support.PayPalConnector
import com.ovea.tadjin.util.Resource
import com.ovea.tadjin.util.properties.PropertySettings
import groovy.json.JsonSlurper
import org.quartz.impl.StdSchedulerFactory

import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.logging.Logger

import org.quartz.*

import static org.quartz.CronScheduleBuilder.cronSchedule
import static org.quartz.JobBuilder.newJob
import static org.quartz.TriggerBuilder.newTrigger

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class BillingConfig {

    private static final Logger LOGGER = Logger.getLogger(BillingConfig.name)

    PropertySettings env
    def json
    String url
    Scheduler scheduler
    Collection<BillingCallback> connectors = []

    BillingConfig(String configLocation, PropertySettings env, String url) throws IllegalBillingConfigException {
        this.env = env
        this.url = url
        this.scheduler = new StdSchedulerFactory().scheduler
        Resource r = Resource.from(configLocation)
        if (!r.exist()) {
            throw new IllegalArgumentException('Missing configLocation: ' + configLocation)
        }
        this.json = new JsonSlurper().parseText(r.readAsString())
        if (!productIds) {
            LOGGER.warning('No products defines in ' + configLocation)
        }
        if (!platformIds) {
            LOGGER.warning('No billing platforms defines in ' + configLocation)
        }
        platformIds.findAll {!BillingPlatform.from(it)}.with {if (it) throw new IllegalBillingConfigException('Unsupported platforms: ' + it); it}
        json.products.each {p, o ->
            (o.platforms ?: [:]).keySet().findAll {!(it in platformIds)}.with {if (it) throw new IllegalBillingConfigException('Unsupported platforms: ' + it + ' for product ' + p); it}
        }
        this.connectors = BillingPlatform.values().findAll {BillingPlatform e -> e.name() in platformIds}.collect {
            switch (it) {
                case BillingPlatform.facebook: return new FacebookConnector(this)
                case BillingPlatform.mpulse: return new MPulseConnector(this)
                case BillingPlatform.paypal: return new PayPalConnector(this)
            }
            throw new AssertionError()
        }
    }

    void schedule(String id, String cron, Closure<?> closure) {
        scheduler.addJob(newJob(QuartzAdapter.class)
            .storeDurably()
            .withIdentity(id + '-job', BillingConfig.simpleName)
            .usingJobData(new JobDataMap([(QuartzAdapter.name): closure]))
            .requestRecovery(true)
            .build(), true);
        scheduler.scheduleJob(newTrigger()
            .withIdentity(id + "-trigger", BillingConfig.simpleName)
            .withSchedule(cronSchedule(cron))
            .forJob(id + "-job", BillingConfig.simpleName)
            .build());
    }

    Collection<String> getProductIds() {
        return json.products?.keySet()
    }

    Collection<String> getPlatformIds() {
        return json.platforms?.keySet()
    }

    boolean supportPlatform(BillingPlatform platform) {
        return platform.name() in platformIds
    }

    boolean supportProduct(String product) {
        return product in productIds
    }

    boolean canPay(String product, BillingPlatform platform) {
        return supportPlatform(platform) && supportProduct(product) && (json.products[product].platforms ?: [:]).containsKey(platform)
    }

    boolean canCancel(String product) {
        return json.products[product].cancellable
    }

    Collection<BillingPlatform> platforms(String product) {
        return (json.products[product].platforms ?: [:]).keySet().collect {it as BillingPlatform}
    }

    @DisallowConcurrentExecution
    public static final class QuartzAdapter implements InterruptableJob {

        private AtomicReference<Thread> runningThread = new AtomicReference<Thread>()

        @Override
        public void interrupt() throws UnableToInterruptJobException {
            Thread running = runningThread.get()
            if (running != null) {
                running.interrupt()
            }
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            Closure<?> job = (Closure) context.jobDetail.jobDataMap.get(QuartzAdapter.name)
            if (job == null) {
                throw new JobExecutionException("Job not found !")
            }
            if (runningThread.compareAndSet(null, Thread.currentThread())) {
                try {
                    job.run()
                } catch (RuntimeException e) {
                    LOGGER.log(Level.SEVERE, "Error in job " + job.getClass().getName() + " : " + e.getMessage(), e)
                    throw new JobExecutionException(e.getMessage(), e)
                } finally {
                    runningThread.set(null)
                }
            } else {
                throw new JobExecutionException("Illegal invocation: job is already running from thread: " + runningThread.get().getName())
            }
        }
    }
}
