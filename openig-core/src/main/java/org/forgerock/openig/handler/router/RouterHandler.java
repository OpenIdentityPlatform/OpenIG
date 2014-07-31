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

package org.forgerock.openig.handler.router;

import static java.lang.String.*;
import static org.forgerock.openig.config.Environment.*;
import static org.forgerock.openig.util.JsonValueUtil.evaluate;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.openig.config.Environment;
import org.forgerock.openig.handler.DispatchHandler;
import org.forgerock.openig.handler.GenericHandler;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.time.TimeService;

/**
 * Auto-configured {@link DispatchHandler}. It looks for route configuration files (very similar to the
 * usual general config file) in a defined directory (by default it looks in {@literal config/routes/}).
 * <pre>
 *   {
 *     "name": "Router",
 *     "type": "Router",
 *     "config": {
 *       "directory": "/tmp/routes",
 *       "defaultHandler": "404NotFound",
 *       "scanInterval": 2
 *     }
 *   }
 * </pre>
 *
 * Note that {@literal scanInterval} is defined in seconds. If {@literal -1} (or any negative value) is
 * provided, only an initial scan is performed at startup, synchronously.
 *
 * @since 2.2
 */
public class RouterHandler extends GenericHandler implements FileChangeListener {

    /**
     * Toolkit to load Routes from monitored files.
     */
    private final RouteBuilder builder;

    /**
     * Monitor a given directory and emit notifications on add/remove/update of files.
     */
    private final DirectoryScanner directoryScanner;

    /**
     * Keep track of managed routes.
     */
    private final Map<File, Route> routes = new HashMap<File, Route>();

    /**
     * Ordered set of managed routes.
     */
    private SortedSet<Route> sorted = new TreeSet<Route>(new LexicographicalRouteComparator());

    /**
     * Protect routes access.
     */
    private final Lock read;

    /**
     * Protects write access to the routes.
     */
    private final Lock write;

    /**
     * The optional handler which should be invoked when no routes match the
     * request.
     */
    private Handler defaultHandler;

    /**
     * Builds a router that loads its configuration from the given directory.
     * @param builder route builder
     * @param scanner {@link DirectoryScanner} that will be invoked at each incoming request
     */
    public RouterHandler(final RouteBuilder builder, final DirectoryScanner scanner) {
        this.builder = builder;
        this.directoryScanner = scanner;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        this.read = lock.readLock();
        this.write = lock.writeLock();
    }

    /**
     * Changes the ordering of the managed routes.
     * @param comparator route comparator
     */
    public void setRouteComparator(final Comparator<Route> comparator) {
        write.lock();
        try {
            SortedSet<Route> newSet = new TreeSet<Route>(comparator);
            newSet.addAll(sorted);
            sorted = newSet;
        } finally {
            write.unlock();
        }
    }

    /**
     * Sets the handler which should be invoked when no routes match the
     * request.
     *
     * @param handler
     *            the handler which should be invoked when no routes match the
     *            request
     */
    public void setDefaultHandler(final Handler handler) {
        write.lock();
        try {
            this.defaultHandler = handler;
        } finally {
            write.unlock();
        }
    }

    /**
     * Starts this handler, executes an initial directory scan.
     */
    public void start() {
        directoryScanner.scan(this);
    }

    /**
     * Stops this handler, shutting down and clearing all the managed routes.
     */
    public void stop() {
        write.lock();
        try {
            // Un-register all the routes
            sorted.clear();
            // Destroy the routes
            for (Route route : routes.values()) {
                route.destroy();
            }
            routes.clear();
        } finally {
            write.unlock();
        }
    }

    @Override
    public void onChanges(final FileChangeSet changes) {
        write.lock();
        try {

            for (File file : changes.getAddedFiles()) {
                onAddedFile(file);
            }

            for (File file : changes.getModifiedFiles()) {
                onModifiedFile(file);
            }

            for (File file : changes.getRemovedFiles()) {
                onRemovedFile(file);
            }

        } finally {
            write.unlock();
        }
    }

    private void onAddedFile(final File file) {
        try {
            Route route = builder.build(file);
            sorted.add(route);
            routes.put(file, route);
            logger.info(format("Added route '%s' defined in file '%s'", route.getName(), file));
        } catch (HeapException e) {
            logger.warning(format(
                    "The route defined in file '%s' cannot be added because it could not be parsed: %s",
                    file, e.getMessage()));
        }
    }

    private void onRemovedFile(final File file) {
        Route route = routes.remove(file);
        if (route != null) {
            sorted.remove(route);
            route.destroy();
            logger.info(format("Removed route '%s' defined in file '%s'", route.getName(), file));
        }
    }

    private void onModifiedFile(final File file) {
        Route newRoute;
        try {
            newRoute = builder.build(file);
        } catch (HeapException e) {
            logger.warning(format(
                    "The route defined in file '%s' cannot be modified because it could not be parsed: %s",
                    file, e.getMessage()));
            return;
        }
        Route oldRoute = routes.remove(file);
        if (oldRoute != null) {
            sorted.remove(oldRoute);
            oldRoute.destroy();
        }
        sorted.add(newRoute);
        routes.put(file, newRoute);
        logger.info(format("Modified route '%s' defined in file '%s'", newRoute.getName(), file));
    }

    @Override
    public void handle(final Exchange exchange) throws HandlerException, IOException {
        // Run the directory scanner
        directoryScanner.scan(this);

        // Traverse the routes
        read.lock();
        try {
            for (Route route : sorted) {
                if (route.accept(exchange)) {
                    route.handle(exchange);
                    return;
                }
            }
            if (defaultHandler != null) {
                defaultHandler.handle(exchange);
                return;
            }
            throw new HandlerException("no handler to dispatch to");
        } finally {
            read.unlock();
        }
    }

    /** Creates and initializes a routing handler in a heap environment. */
    public static class Heaplet extends NestedHeaplet {

        @Override
        public Object create() throws HeapException {

            // By default, uses the config/routes from the environment
            Environment env = (Environment) heap.get(ENVIRONMENT_HEAP_KEY);
            File directory = new File(env.getConfigDirectory(), "routes");

            // Configuration can override that value
            String evaluation = evaluate(config.get("directory"));
            if (evaluation != null) {
                directory = new File(evaluation);
            }

            DirectoryScanner scanner = new DirectoryMonitor(directory);

            int period = config.get("scanInterval").defaultTo(PeriodicDirectoryScanner.TEN_SECONDS).asInteger();
            if (period > 0) {
                // Wrap the scanner in another scanner that will trigger scan at given interval
                PeriodicDirectoryScanner periodic =
                        new PeriodicDirectoryScanner(scanner, TimeService.SYSTEM);

                // configuration values is expressed in seconds, needs to convert it to milliseconds
                periodic.setScanInterval(period * 1000);
                scanner = periodic;
            } else {
                // Only scan once when handler.start() is called
                scanner = new OnlyOnceDirectoryScanner(scanner);
            }

            RouterHandler handler = new RouterHandler(new RouteBuilder(heap), scanner);
            handler.setDefaultHandler(HeapUtil.getObject(heap, config.get("defaultHandler"),
                    Handler.class));
            return handler;
        }

        @Override
        public void start() throws HeapException {
            ((RouterHandler) object).start();
        }

        @Override
        public void destroy() {
            ((RouterHandler) object).stop();
        }
    }
}
