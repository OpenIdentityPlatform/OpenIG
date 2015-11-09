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
import static org.forgerock.http.filter.Filters.newSessionFilter;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.util.Json.*;
import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.util.Utils.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.audit.AuditService;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.session.SessionManager;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.HttpAccessAuditFilter;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Keys;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.Logger;
import org.forgerock.util.time.TimeService;

/**
 * Builder for new {@link Route}s.
 *
 * @since 2.2
 */
class RouteBuilder {

    /**
     * Heap to be used as parent for routes built from this builder.
     */
    private final HeapImpl heap;
    private final Name name;

    /**
     * Builds a new builder.
     * @param heap parent heap for produced routes
     * @param name router name (used as parent name)
     */
    RouteBuilder(final HeapImpl heap, final Name name) {
        this.heap = heap;
        this.name = name;
    }

    /**
     * Builds a new route from the given resource file.
     *
     * @param resource route definition
     * @return a new configured Route
     * @throws HeapException if the new Route cannot be build
     */
    Route build(final File resource) throws HeapException {
        final JsonValue config = readJson(resource);
        final Name routeHeapName = this.name.child(resource.getPath());
        final String defaultRouteName = resource.getName();

        return build(config, routeHeapName, defaultRouteName);
    }
    /**
     * Builds a new route from the given resource file.
     *
     * @param resource route definition
     * @return a new configured Route
     * @throws HeapException if the new Route cannot be build
     */
    Route build(final JsonValue config, final Name routeHeapName, final String defaultRouteName) throws HeapException {
        HeapImpl routeHeap = new HeapImpl(heap, routeHeapName);
        routeHeap.init(config, "handler", "session", "name", "condition", "logSink", "audit-service",
                "globalDecorators");

        SessionManager sessionManager = routeHeap.resolve(config.get("session"), SessionManager.class, true);
        String routeName = config.get("name").defaultTo(defaultRouteName).asString();
        Expression<Boolean> condition = asExpression(config.get("condition"), Boolean.class);

        final LogSink logSink = heap.resolve(config.get("logSink").defaultTo(LOGSINK_HEAP_KEY), LogSink.class);
        Logger logger = new Logger(logSink, routeHeapName);

        AuditService auditService = heap.resolve(config.get("audit-service"), AuditService.class, true);
        Handler handler = setupRouteHandler(routeHeap.getHandler(), logger, sessionManager, auditService);

        return new Route(routeHeap, handler, routeName, condition);
    }

    private Handler setupRouteHandler(Handler handler,
                                      Logger logger,
                                      SessionManager sessionManager,
                                      AuditService auditService) throws HeapException {
        List<Filter> filters = new ArrayList<>();
        if (sessionManager != null) {
            filters.add(newSessionFilter(sessionManager));
        }
        if (auditService != null) {
            filters.add(new HttpAccessAuditFilter(auditService,
                                                  heap.get(Keys.TIME_SERVICE_HEAP_KEY, TimeService.class)));
        }
        return chainOf(handler, filters);
    }

    /**
     * Reads the raw Json content from the route's definition file.
     *
     * @param resource
     *            The route definition file.
     * @return Json structure
     * @throws HeapException
     *             if there are IO or parsing errors
     */
    private JsonValue readJson(final File resource) throws HeapException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(resource);
            return new JsonValue(readJsonLenient(fis));
        } catch (FileNotFoundException e) {
            throw new HeapException(format("File %s does not exist", resource), e);
        } catch (IOException e) {
            throw new HeapException(format("Cannot read/parse content of %s", resource), e);
        } finally {
            closeSilently(fis);
        }
    }

}
