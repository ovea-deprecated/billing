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

import com.ovea.billing.support.BangoConnector
import com.ovea.billing.support.FacebookConnector
import com.ovea.billing.support.MPulseConnector
import com.ovea.billing.support.PayPalConnector
import com.ovea.tadjin.util.Resource
import com.ovea.tadjin.util.properties.PropertySettings
import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory

import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.logging.Logger

import static org.quartz.CronScheduleBuilder.cronSchedule
import static org.quartz.JobBuilder.newJob
import static org.quartz.TriggerBuilder.newTrigger

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class BillingConfig {

    private static final Logger LOGGER = Logger.getLogger(BillingConfig.name)

    private final def json
    final String url
    final Scheduler scheduler
    final Collection<BillingCallback> connectors = []

    BillingConfig(String configLocation, PropertySettings env, String url) throws IllegalBillingConfigException {
        this.url = url
        this.scheduler = new StdSchedulerFactory().scheduler
        Resource r = Resource.from(configLocation)
        if (!r.exist()) {
            throw new IllegalArgumentException('Missing configLocation: ' + configLocation)
        }
        Map ctx = (env.properties + System.getenv() + System.properties) as TreeMap
        this.json = new JsonSlurper().parseText(new SimpleTemplateEngine().createTemplate(r.readAsString()).make(ctx) as String)
        if (!productIds) {
            LOGGER.warning('No products defines in ' + configLocation)
        }
        if (!platformIds) {
            LOGGER.warning('No billing platforms defines in ' + configLocation)
        }
        platformIds.findAll { !BillingPlatform.from(it) }.with { if (it) throw new IllegalBillingConfigException('Unsupported platforms: ' + it); it }
        json.products.each { p, o ->
            (o.platforms ?: [:]).keySet().findAll { !(it in platformIds) }.with { if (it) throw new IllegalBillingConfigException('Unsupported platforms: ' + it + ' for product ' + p); it }
        }
        this.connectors = BillingPlatform.values().findAll { BillingPlatform e -> e.name() in platformIds }.collect {
            switch (it) {
                case BillingPlatform.facebook: return new FacebookConnector(this)
                case BillingPlatform.mpulse: return new MPulseConnector(this)
                case BillingPlatform.paypal: return new PayPalConnector(this)
                case BillingPlatform.bango: return new BangoConnector(this)
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

    def getPlatformConfig(BillingPlatform platform) {
        return json.platforms[platform.name()] ?: [:]
    }

    def getProductConfig(String product) {
        return json.products?."${product}" ?: [:]
    }

    def getProductConfig(BillingPlatform platform, String product) {
        return getProductConfig(product).platforms."${platform.name()}" ?: [:]
    }

    boolean supportPlatform(BillingPlatform platform) {
        return platform.name() in platformIds
    }

    boolean supportProduct(String product) {
        return product in productIds
    }

    boolean canPay(String product, BillingPlatform platform) {
        return supportPlatform(platform) && supportProduct(product) && (json.products[product].platforms ?: [:]).containsKey(platform.name())
    }

    boolean canCancel(String product) {
        return json.products[product].cancellable
    }

    String getCallbackUrl(BillingPlatform platform) {
        return url + '/callback/' + platform
    }

    Collection<BillingPlatform> platforms(String product) {
        return (json.products[product].platforms ?: [:]).keySet().collect { it as BillingPlatform }
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
