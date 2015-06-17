/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openig.filter;

import static org.forgerock.util.time.Duration.duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

public class BucketMain {

    private static class BucketRunnable implements Runnable {

        private TokenBucket bucket;
        private boolean stop;
        private Meter injectorMeter;
        private Meter outputMeter;
        private CountDownLatch latch;

        public BucketRunnable(TokenBucket bucket, MetricRegistry registry, CountDownLatch latch) {
            this.latch = latch;
            this.bucket = bucket;
            this.stop = false;
            this.injectorMeter = registry.meter("injector");
            this.outputMeter = registry.meter("output");
        }

        @Override
        public void run() {
            try {
                latch.await();
            } catch (InterruptedException e1) {
                throw new RuntimeException(e1);
            }

            while (!stop) {
                injectorMeter.mark();
                long delay = bucket.tryConsume();
                if (delay <= 0) {
                    outputMeter.mark();

                }

                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void stop() {
            this.stop = true;
        }
    }

    static final MetricRegistry METRICS = new MetricRegistry();

    public static void main(String[] args) throws InterruptedException {
        TimeService time = TimeService.SYSTEM;
        startReport();
        TokenBucket bucket = new TokenBucket(time, 20, duration("1 seconds"));

        final int count = 10;
        List<BucketRunnable> runnables = new ArrayList<>(count);
        ExecutorService threadPool = Executors.newFixedThreadPool(count);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < count; i++) {
            BucketRunnable runnable = new BucketRunnable(bucket, METRICS, latch);
            runnables.add(runnable);
            threadPool.submit(runnable);
        }
        latch.countDown();

        wait(duration("3 minutes"));
        for (BucketRunnable runnable : runnables) {
            runnable.stop();
        }
        threadPool.shutdown();
        threadPool.awaitTermination(10, TimeUnit.SECONDS);

        Meter outputMeter = METRICS.meter("output");
        System.out.printf("Final output mean rate is %f %n", outputMeter.getMeanRate());
    }

    static void startReport() {
        ConsoleReporter reporter = ConsoleReporter.forRegistry(METRICS)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(20, TimeUnit.SECONDS);
    }

    static void wait(Duration duration) {
        try {
            Thread.sleep(duration.to(TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
