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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.forgerock.openig.util.Duration;

/**
 * ThreadSafeCache is a thread-safe write-through cache.
 * <p>
 * Instead of storing directly the value in the backing Map, it requires the consumer to provide a value factory (a
 * Callable). A new FutureTask encapsulate the callable, is executed and is placed inside a ConcurrentHashMap if absent.
 * <p>
 * The final behavior is that, even if two concurrent Threads are borrowing an object from the cache,
 * given that they provide an equivalent value factory, the first one will compute the value while the other will get
 * the result from the Future (and will wait until the result is computed or a timeout occurs).
 *
 * @param <K> Type of the key
 * @param <V> Type of the value
 */
public class ThreadSafeCache<K, V> {

    private final ScheduledExecutorService executorService;
    private final ConcurrentMap<K, Future<V>> cache = new ConcurrentHashMap<K, Future<V>>();
    private volatile Duration timeout = new Duration(1L, TimeUnit.MINUTES);

    /**
     * Build a new {@link ThreadSafeCache} using the given scheduled executor.
     *
     * @param executorService
     *         scheduled executor for registering expiration callbacks.
     */
    public ThreadSafeCache(final ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Sets the cache entry expiration delay. Notice that this will impact only new cache entries.
     *
     * @param timeout new cache entry timeout
     */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    private Future<V> createIfAbsent(final K key, final Callable<V> callable) {
        Future<V> future = cache.get(key);
        if (future == null) {
            final FutureTask<V> futureTask = new FutureTask<V>(callable);
            future = cache.putIfAbsent(key, futureTask);
            if (future == null) {
                future = futureTask;

                // Compute the value
                futureTask.run();

                // Register cache entry expiration time
                executorService.schedule(new Expiration(key),
                                         timeout.getValue(),
                                         timeout.getUnit());
            }
        }
        return future;
    }

    /**
     * Borrow (and create before hand if absent) a cache entry. If another Thread has created (or the creation is
     * undergoing) the value, this methods waits indefinitely for the value to be available.
     *
     * @param key
     *         entry key
     * @param callable
     *         cached value factory
     * @return the cached value
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting
     * @throws ExecutionException
     *         if the cached value computation threw an exception
     */
    public V getValue(final K key, final Callable<V> callable) throws InterruptedException, ExecutionException {
        try {
            return createIfAbsent(key, callable).get();
        } catch (InterruptedException e) {
            cache.remove(key);
            throw e;
        } catch (ExecutionException e) {
            cache.remove(key);
            throw e;
        } catch (RuntimeException e) {
            cache.remove(key);
            throw e;
        }
    }

    /**
     * Clean-up the cache entries.
     */
    public void clear() {
        // Clear the cache
        cache.clear();
    }

    /**
     * Registered in the executor, this callable simply removes the cache entry after a specified amount of time.
     */
    private class Expiration implements Callable<Object> {
        private final K key;

        public Expiration(final K key) {
            this.key = key;
        }

        @Override
        public Object call() throws Exception {
            return cache.remove(key);
        }
    }
}
