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

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.forgerock.http.util.Json.readJsonLenient;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValueFunctions.duration;
import static org.forgerock.json.JsonValueFunctions.file;
import static org.forgerock.json.resource.Resources.newHandler;
import static org.forgerock.json.resource.http.CrestHttp.newHttpHandler;
import static org.forgerock.openig.handler.router.Route.routeName;
import static org.forgerock.openig.heap.Keys.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.CrestUtil.newCrestApplication;
import static org.forgerock.openig.util.JsonValues.optionalHeapObject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Responses;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.services.context.Context;
import org.forgerock.util.Reject;
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
     * Monitor a given directory and emit notifications on add/remove/update of files.
     */
    private final DirectoryMonitor directoryMonitor;

    /**
     * Keep track of managed routes.
     */
    private final Map<String, Route> routes = new HashMap<>();

    /**
     * Ordered set of managed routes.
     */
    private final SortedSet<Route> sorted = new TreeSet<>(new LexicographicalRouteComparator());

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
     * @param directoryMonitor the directory monitor
     */
    public RouterHandler(final RouteBuilder builder, final DirectoryMonitor directoryMonitor) {
        this.builder = builder;
        this.directoryMonitor = directoryMonitor;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        this.read = lock.readLock();
        this.write = lock.writeLock();
    }

    /**
     * Sets the handler which should be invoked when no routes match the
     * request.
     *
     * @param handler
     *            the handler which should be invoked when no routes match the
     *            request
     */
    void setDefaultHandler(final Handler handler) {
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

    /**
     * Deploy a route, meaning that it loads it but also stores it in a file.
     *
     * @param routeId the id of the route to deploy
     * @param routeName the name of the route to deploy
     * @param routeConfig the configuration of the route to deploy
     *
     * @throws RouterHandlerException if the given routeConfig is not valid
     */
    public void deploy(String routeId, String routeName, JsonValue routeConfig) throws RouterHandlerException {
        Reject.ifNull(routeName);
        write.lock();
        try {
            load(routeId, routeName, routeConfig.copy());
            directoryMonitor.store(routeId, routeConfig);
            logger.info("Deployed the route with id '{}' named '{}'", routeId, routeName);
        } catch (IOException e) {
            throw new RouterHandlerException(format("An error occurred while storing the route '%s'", routeId), e);
        } finally {
            write.unlock();
        }
    }

    /**
     * Undeploy a route, meaning that it unloads it but also deletes the associated file.
     * @param routeId the id of the route to remove
     *
     * @return  the configuration of the undeployed route
     * @throws RouterHandlerException if the given routeId is not valid
     */
    public JsonValue undeploy(String routeId) throws RouterHandlerException {
        write.lock();
        try {
            JsonValue routeConfig = unload(routeId);
            directoryMonitor.delete(routeId);
            logger.info("Undeployed the route with id '{}'", routeId);
            return routeConfig;
        } finally {
            write.unlock();
        }
    }

    /**
     * Update a route.
     * @param routeId the id of the route to update
     * @param routeName the name of the route to update
     * @param routeConfig the new route's configuration
     *
     * @throws RouterHandlerException if the given routeConfig is not valid
     */
    public void update(String routeId, String routeName, JsonValue routeConfig) throws RouterHandlerException {
        write.lock();
        Route previousRoute = routes.get(routeId);
        try {
            Reject.ifNull(routeId, routeName);
            write.lock();
            try {
                unload(routeId);
                load(routeId, routeName, routeConfig);
            } finally {
                write.unlock();
            }
            directoryMonitor.store(routeId, routeConfig);
            logger.info("Updated the route with id '{}'", routeId);
        } catch (RouterHandlerException e) {
            // Restore the previous route if the reload failed
            load(previousRoute.getId(), previousRoute.getName(), previousRoute.getConfig());
            throw e;
        } catch (IOException e) {
            throw new RouterHandlerException(format("An error occurred while storing the route '%s'", routeId), e);
        } finally {
            write.unlock();
        }
    }

    void load(String routeId, String routeName, JsonValue routeConfig) throws RouterHandlerException {
        Reject.ifNull(routeId, routeName);
        write.lock();
        try {
            for (Route route : sorted) {
                if (routeId.equals(route.getId())) {
                    throw new RouterHandlerException(format("A route with the id '%s' is already loaded", routeId));
                }
                if (routeName.equals(route.getName())) {
                    throw new RouterHandlerException(
                            format("A route with the id '%s' is already loaded with the name '%s'",
                                   routeId,
                                   routeName));
                }
            }
            Route route;
            try {
                route = builder.build(routeId, routeName, routeConfig);
            } catch (HeapException e) {
                throw new RouterHandlerException(
                        format("An error occurred while loading the route with the '%s'", routeName), e);
            }
            route.start();
            routes.put(routeId, route);
            sorted.add(route);
            logger.info("Loaded the route with id '{}' registered with the name '{}'", route.getId(), route.getName());
        } finally {
            write.unlock();
        }
    }

    JsonValue unload(String routeId) throws RouterHandlerException {
        Reject.ifNull(routeId);
        write.lock();
        try {
            Route removedRoute = routes.remove(routeId);
            if (removedRoute == null) {
                throw new RouterHandlerException(format("No route with id '%s' was loaded : unable to unload it.",
                                                        routeId));
            } else {
                removedRoute.destroy();
                logger.info("Unloaded the route with id '{}'", routeId);
            }

            Iterator<Route> iterator = sorted.iterator();
            while (iterator.hasNext()) {
                if (removedRoute == iterator.next()) {
                    iterator.remove();
                }
            }
            return removedRoute.getConfig();
        } finally {
            write.unlock();
        }
    }

    JsonValue routeConfig(String routeId) throws RouterHandlerException {
        Reject.ifNull(routeId);
        read.lock();
        try {
            Route route = routes.get(routeId);
            if (route == null) {
                throw new RouterHandlerException(format("No route with id '%s' was loaded.", routeId));
            }
            return route.getConfig();
        } finally {
            read.unlock();
        }
    }

    /**
     * Returns a list of the currently deployed routes, in the order they are tried.
     * @return a list of the currently deployed routes, in the order they are tried.
     */
    List<Route> getRoutes() {
        read.lock();
        try {
            return new ArrayList<>(sorted);
        } finally {
            read.unlock();
        }
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

    @Override
    public void onChanges(FileChangeSet changes) {
        for (File file : changes.getRemovedFiles()) {
            try {
                onRemovedFile(file);
            } catch (Exception e) {
                logger.error("An error occurred while handling the removed file '{}'", file.getAbsolutePath(), e);
            }
        }

        for (File file : changes.getAddedFiles()) {
            try {
                onAddedFile(file);
            } catch (Exception e) {
                logger.error("An error occurred while handling the added file '{}'", file.getAbsolutePath(), e);
            }
        }

        for (File file : changes.getModifiedFiles()) {
            try {
                onModifiedFile(file);
            } catch (Exception e) {
                logger.error("An error occurred while handling the modified file '{}'", file.getAbsolutePath(), e);
            }
        }
    }

    private void onAddedFile(File file) {
        try (FileReader fileReader = new FileReader(file)) {
            JsonValue routeConfig = routeConfig(fileReader);
            String routeId = routeId(file);
            String routeName = routeName(routeConfig, routeId);
            load(routeId, routeName, routeConfig);
        } catch (IOException | JsonValueException e) {
            logger.error("The file '{}' is not a valid route configuration.", file, e);
        } catch (RouterHandlerException e) {
            logger.error("An error occurred while reading the route defined in the file '{}'.", file, e);
        }
    }

    private void onRemovedFile(File file) {
        try {
            unload(routeId(file));
        } catch (RouterHandlerException e) {
            // No route with id routeId was found. Just ignore.
            logger.warn("The file '{}' has not been loaded yet, removal ignored.", file, e);
        }
    }

    private void onModifiedFile(File file) {
        onRemovedFile(file);
        onAddedFile(file);
    }

    private static JsonValue routeConfig(FileReader fileReader) throws IOException {
        return json(readJsonLenient(fileReader));
    }

    private static String routeId(File file) {
        return withoutDotJson(file.getName());
    }

    private static String withoutDotJson(final String path) {
        return path.substring(0, path.length() - ".json".length());
    }

    /** Creates and initializes a routing handler in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        private EndpointRegistry.Registration registration;
        private DirectoryMonitor directoryMonitor;
        private ScheduledFuture<?> scheduledCommand;
        private Duration scanInterval;


        @Override
        public Object create() throws HeapException {
            File directory = config.get("directory").as(evaluatedWithHeapProperties()).as(file());
            if (directory == null) {
                // By default, uses the config/routes from the environment
                Environment env = heap.get(ENVIRONMENT_HEAP_KEY, Environment.class);
                directory = new File(env.getConfigDirectory(), "routes");
            }
            this.directoryMonitor = new DirectoryMonitor(directory);
            this.scanInterval = scanInterval();

            EndpointRegistry registry = endpointRegistry();
            RouterHandler handler = new RouterHandler(new RouteBuilder((HeapImpl) heap,
                                                                       qualified,
                                                                       registry),
                                                      directoryMonitor);
            handler.setDefaultHandler(config.get("defaultHandler").as(optionalHeapObject(heap, Handler.class)));

            // Register the /routes/* endpoint
            final RequestHandler routesCollection = newHandler(new RoutesCollectionProvider(handler));
            registration = registry.register("routes",
                                             newHttpHandler(newCrestApplication(routesCollection,
                                                                                "frapi:openig:handler")));
            logger.info("Routes endpoint available at '{}'", registration.getPath());

            return handler;
        }

        private Duration scanInterval() {
            JsonValue scanIntervalConfig = config.get("scanInterval")
                                                 .as(evaluatedWithHeapProperties())
                                                 .defaultTo("10 seconds");
            if (scanIntervalConfig.isNumber()) {
                // Backward compatibility : configuration values is expressed in seconds only
                Integer scanIntervalSeconds = scanIntervalConfig.asInteger();
                if (scanIntervalSeconds <= 0) {
                    logger.warn("Prefer to declare to declare the scanInterval as \"disabled\".");
                    return Duration.ZERO;
                }
                logger.warn("Prefer to declare to declare the scanInterval configuration setting as a duration "
                                    + "like this : \"" + scanIntervalSeconds + " seconds\".");
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
            Runnable command = new Runnable() {
                @Override
                public void run() {
                    try {
                        directoryMonitor.monitor((RouterHandler) object);
                    } catch (Exception e) {
                        logger.error("An error occurred while scanning the directory", e);
                    }
                }
            };

            // First scan is blocking : ensure everything is correct
            command.run();

            // If a scanInterval was provided then schedule the next directory scans
            if (scanInterval != Duration.ZERO) {
                ScheduledExecutorService scheduledExecutorService =
                        heap.get(SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY, ScheduledExecutorService.class);

                scheduledCommand = scheduledExecutorService.scheduleAtFixedRate(command,
                                                                                scanInterval.to(MILLISECONDS),
                                                                                scanInterval.to(MILLISECONDS),
                                                                                MILLISECONDS);
            }
        }

        @Override
        public void destroy() {
            if (scheduledCommand != null) {
                scheduledCommand.cancel(true);
            }
            if (object != null) {
                ((RouterHandler) object).stop();
            }
            if (registration != null) {
                registration.unregister();
            }
            super.destroy();
        }
    }
}
