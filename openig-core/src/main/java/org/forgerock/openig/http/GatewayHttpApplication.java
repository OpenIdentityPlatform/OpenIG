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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static java.lang.String.format;
import static org.forgerock.openig.config.Environment.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.io.TemporaryStorage.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.log.LogSink.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.util.JsonValueUtil.getWithDeprecation;
import static org.forgerock.util.Utils.closeSilently;

import org.forgerock.http.Handler;
import org.forgerock.http.Handlers;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.SessionFilter;
import org.forgerock.http.SessionManager;
import org.forgerock.http.io.Buffer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.ConsoleLogSink;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.util.HttpClient;
import org.forgerock.util.Factory;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.Map;

/**
 * Configuration class for configuring the OpenIG Gateway.
 *
 * @since 3.1.0
 */
public final class GatewayHttpApplication implements HttpApplication {

    /**
     * Key to retrieve the default {@link SessionManager} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    private static final String SESSION_FACTORY_HEAP_KEY = "Session";

    private HeapImpl heap;
    private TemporaryStorage storage;
    private Handler httpHandler;

    public GatewayHttpApplication() throws Exception {
        init();
    }

    public void init() throws Exception {
        Environment environment = new GatewayEnvironment();

        try {
            // Load the configuration
            File configuration = new File(environment.getConfigDirectory(), "config.json");
            URL configurationURL = configuration.canRead() ? configuration.toURI().toURL() : getClass()
                    .getResource("default-config.json");
            JsonValue config = readJson(configurationURL);

            // Create and configure the heap
            heap = new HeapImpl();
            // "Live" objects
            heap.put(ENVIRONMENT_HEAP_KEY, environment);

            // can be overridden in config
            TemporaryStorage temporaryStorage = new TemporaryStorage();
            heap.put(TEMPORARY_STORAGE_HEAP_KEY, temporaryStorage);
            heap.put(LOGSINK_HEAP_KEY, new ConsoleLogSink());
            heap.put(HttpClient.HTTP_CLIENT_HEAP_KEY, new HttpClient(temporaryStorage));
            heap.init(config.get("heap").required().expect(Map.class));

            // As all heaplets can specify their own storage and logger,
            // these two lines provide custom logger or storage available.
            Logger logger = new Logger(heap.resolve(config.get("logSink").defaultTo(LOGSINK_HEAP_KEY),
                    LogSink.class, true), "GatewayServlet");
            storage = heap.resolve(config.get("temporaryStorage").defaultTo(TEMPORARY_STORAGE_HEAP_KEY),
                    TemporaryStorage.class);
            // Let the user change the type of session to use
            SessionManager sessionManager = heap.get(SESSION_FACTORY_HEAP_KEY, SessionManager.class);
            SessionFilter sessionFilter = new SessionFilter(sessionManager);
            org.forgerock.openig.handler.Handler handler = heap.resolve(
                    getWithDeprecation(config, logger, "handler", "handlerObject"),
                    org.forgerock.openig.handler.Handler.class);
            URI baseURI = config.get("baseURI").asURI();

            httpHandler = Handlers.chain(new HttpHandler(handler, baseURI), sessionFilter);
        } catch (Exception e) {
            HttpHandler.LOG.error("Failed to initialise Http Application", e);
            throw e;
        }
    }

    @Override
    public Handler start() {
        return httpHandler;
    }

    @Override
    public Factory<Buffer> getBufferFactory() {
        return storage;
    }

    @Override
    public void stop() {
        heap.destroy();
    }

    private static JsonValue readJson(URL resource) throws IOException {
        InputStreamReader reader = null;
        try {
            InputStream in = resource.openStream();
            JSONParser parser = new JSONParser();
            reader = new InputStreamReader(in);
            return new JsonValue(parser.parse(reader));
        } catch (ParseException e) {
            throw new IOException(format("Cannot parse %s, probably because of some malformed Json", resource), e);
        } catch (FileNotFoundException e) {
            throw new IOException(format("File %s does not exists", resource), e);
        } finally {
            closeSilently(reader);
        }
    }
}
