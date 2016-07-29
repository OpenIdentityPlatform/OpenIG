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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;
import static org.forgerock.http.routing.RoutingMode.EQUALS;
import static org.forgerock.json.JsonValueFunctions.duration;
import static org.forgerock.json.JsonValueFunctions.file;
import static org.forgerock.openig.heap.Keys.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.optionalHeapObject;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Responses;
import org.forgerock.http.routing.Router;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.handler.Handlers;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Auto-configured {@link org.forgerock.openig.handler.DispatchHandler}.
 * It looks for route configuration files (very similar to the usual general config file)
 * in a defined directory (by default it looks in {@literal config/routes/}).
 * <pre>
 *   {@code
 *   {
 *     "name": "Router",
 *     "type": "Router",
 *     "config": {
 *       "directory": "/tmp/routes",
 *       "defaultHandler": "404NotFound",
 *       "scanInterval": 2 or "2 seconds"
 *     }
 *   }
 *   }
 * </pre>
 *
 * Note that {@literal scanInterval} can be defined in 2 ways :
 * <ul>
 *     <li>as an integer, which defines the number of seconds. If {@literal -1} (or any negative value) is provided,
 *     only an initial scan is performed at startup, synchronously.</li>
 *     <li>as a duration. If "disabled" or "zero" is provided, only an initial scan is performed at startup,
 *     synchronously.</li>
 * </ul>
 * In both cases, the default value is 10 seconds.
 *
 * @since 2.2
 */
public class RouterHandler extends GenericHeapObject implements FileChangeListener, Handler {

    private static final Logger logger = LoggerFactory.getLogger(RouterHandler.class);

    /**
     * Toolkit to load Routes from monitored files.
     */
    private final RouteBuilder builder;

    /**
     * Keep track of managed routes.
     */
    private final Map<File, Route> routes = new HashMap<>();

    /**
     * Ordered set of managed routes.
     */
    private SortedSet<Route> sorted = new TreeSet<>(new LexicographicalRouteComparator());

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
     */
    public RouterHandler(final RouteBuilder builder) {
        this.builder = builder;
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
            SortedSet<Route> newSet = new TreeSet<>(comparator);
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

            for (File file : changes.getRemovedFiles()) {
                onRemovedFile(file);
            }

            for (File file : changes.getAddedFiles()) {
                onAddedFile(file);
            }

            for (File file : changes.getModifiedFiles()) {
                onModifiedFile(file);
            }

        } finally {
            write.unlock();
        }
    }

    private void onAddedFile(final File file) {
        Route route;
        try {
            route = builder.build(file);
        } catch (Exception e) {
            logger.error("The route defined in file '{}' cannot be added", file, e);
            return;
        }
        String name = route.getName();
        if (sorted.contains(route)) {
            logger.error("The added file '{}' contains a route named '{}' that is already registered by the file '{}'",
                         file,
                         name,
                         lookupRouteFile(name));
            route.destroy();
            return;
        }
        route.start();
        sorted.add(route);
        routes.put(file, route);
        logger.info("Added route '{}' defined in file '{}'", name, file);
    }

    private void onRemovedFile(final File file) {
        Route route = routes.remove(file);
        if (route != null) {
            sorted.remove(route);
            route.destroy();
            logger.info("Removed route '{}' defined in file '{}'", route.getName(), file);
        }
    }

    private void onModifiedFile(final File file) {
        Route newRoute;
        try {
            newRoute = builder.build(file);
        } catch (Exception e) {
            logger.error("The route defined in file '{}' cannot be modified", file, e);
            return;
        }
        Route oldRoute = routes.get(file);
        if (oldRoute != null) {
            // Route did change its name, and the new name is already in use
            if (!oldRoute.getName().equals(newRoute.getName()) && sorted.contains(newRoute)) {
                logger.error("The modified file '{}' contains a route named '{}' that is already "
                                     + "registered by the file '{}'",
                             file,
                             newRoute.getName(),
                             lookupRouteFile(newRoute.getName()));
                newRoute.destroy();
                return;
            }
            routes.remove(file);
            sorted.remove(oldRoute);
            oldRoute.destroy();
        }
        newRoute.start();
        sorted.add(newRoute);
        routes.put(file, newRoute);
        logger.info("Modified route '{}' defined in file '{}'", newRoute.getName(), file);
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        // Traverse the routes
        read.lock();
        try {
            for (Route route : sorted) {
                if (route.accept(context, request)) {
                    return route.handle(context, request);
                }
            }
            if (defaultHandler != null) {
                return defaultHandler.handle(context, request);
            }
            logger.error("no handler to dispatch to");
            return Promises.newResultPromise(Responses.newNotFound());
        } finally {
            read.unlock();
        }
    }

    private File lookupRouteFile(String routeName) {
        for (Map.Entry<File, Route> entry : routes.entrySet()) {
            File file = entry.getKey();
            Route route = entry.getValue();

            if (route.getName().equals(routeName)) {
                return file;
            }
        }
        return null;
    }

    /** Creates and initializes a routing handler in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        private EndpointRegistry.Registration registration;
        private DirectoryScanner scanner;

        @Override
        public Object create() throws HeapException {
            File directory = config.get("directory").as(evaluatedWithHeapBindings()).as(file());
            if (directory == null) {
                // By default, uses the config/routes from the environment
                Environment env = heap.get(ENVIRONMENT_HEAP_KEY, Environment.class);
                directory = new File(env.getConfigDirectory(), "routes");
            }
            DirectoryMonitor directoryMonitor = new DirectoryMonitor(directory);

            this.scanner = directoryScanner(directoryMonitor, scanInterval());

            // Register the /routes/* endpoint
            Router routes = new Router();
            routes.addRoute(requestUriMatcher(EQUALS, ""), Handlers.NO_CONTENT);
            EndpointRegistry registry = endpointRegistry();
            registration = registry.register("routes", routes);

            RouterHandler handler = new RouterHandler(new RouteBuilder((HeapImpl) heap,
                                                                       qualified,
                                                                       new EndpointRegistry(routes,
                                                                                            registration.getPath())));
            handler.setDefaultHandler(config.get("defaultHandler").as(optionalHeapObject(heap, Handler.class)));

            // The RouterHandler will listen the FileChangeSet notifications
            scanner.register(handler);

            return handler;
        }

        private DirectoryScanner directoryScanner(DirectoryMonitor directoryMonitor, Duration scanInterval)
                throws HeapException {
            if (scanInterval != Duration.ZERO) {
                ScheduledExecutorService scheduledExecutor =
                        heap.get(SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY, ScheduledExecutorService.class);
                // Wrap the scanner in another scanner that will trigger scan at given interval
                PeriodicDirectoryScanner periodic = new PeriodicDirectoryScanner(directoryMonitor, scheduledExecutor);

                periodic.setScanInterval(scanInterval.to(TimeUnit.MILLISECONDS));
                return periodic;
            } else {
                // Only scan once when handler.start() is called
                return new OnlyOnceDirectoryScanner(directoryMonitor);
            }
        }

        private Duration scanInterval() {
            JsonValue scanIntervalConfig = config.get("scanInterval")
                                                 .as(evaluatedWithHeapBindings())
                                                 .defaultTo("10 seconds");
            if (scanIntervalConfig.isNumber()) {
                // Backward compatibility : configuration values is expressed in seconds only
                Integer scanIntervalSeconds = scanIntervalConfig.asInteger();
                logger.warn("Prefer to declare to declare the scanInterval configuration setting as a duration "
                                       + "like this : \"" + scanIntervalSeconds + " seconds\".");
                if (scanIntervalSeconds == -1) {
                    return Duration.ZERO;
                }
                return Duration.duration(scanIntervalConfig.asInteger(), TimeUnit.SECONDS);
            } else {
                Duration scanInterval = scanIntervalConfig.as(duration());
                if (scanInterval.isUnlimited()) {
                    throw new JsonValueException(scanIntervalConfig,
                                                 "unlimited duration is not allowed for the setting scanInterval");
                }
                return scanInterval;
            }
        }

        @Override
        public void start() throws HeapException {
            scanner.start();
        }

        @Override
        public void destroy() {
            if (object != null) {
                ((RouterHandler) object).stop();
            }
            if (registration != null) {
                registration.unregister();
            }
            scanner.stop();
            super.destroy();
        }
    }
}
