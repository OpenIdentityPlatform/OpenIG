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
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.openig.handler.router;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Responses;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.handler.router.FileChangeListener;
import org.forgerock.openig.handler.router.FileChangeSet;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.forgerock.json.JsonValueFunctions.duration;
import static org.forgerock.json.JsonValueFunctions.file;
import static org.forgerock.openig.heap.Keys.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

/**
 * An {@link Handler} that validates incoming requests and outgoing responses against one
 * or more OpenAPI (Swagger) specifications loaded from a monitored directory.
 *
 * <p>On each request, every loaded validator is tried in turn. A request is dispatched to the
 * {@link #upstreamHandler} only when it matches a specification without errors. If the request
 * matches a specification but violates its constraints, a {@code 400 Bad Request} response is
 * returned immediately. If no specification recognises the request path or method, a
 * {@code 404 Not Found} response is returned.
 *
 * <p>Upstream responses are also validated against the matched specification. A response that
 * does not conform to the specification is replaced with a {@code 502 Bad Gateway} response.
 *
 * <p>Specification files are watched via a {@link SwaggerDirectoryMonitor}; files can be added,
 * removed, or modified at runtime without restarting the gateway. The associated
 * {@link Heaplet} wires the monitor to a {@link ScheduledExecutorService} so that the directory
 * is re-scanned at a configurable interval (default: 10 seconds).
 * <pre>
 *    {@code
 *    {
 *      "name": "SwaggerRouter",
 *      "type": "SwaggerRouter",
 *      "config": {
 *        "directory": "/config/swagger",
 *        "handler": "ClientHandler",
 *        "scanInterval": "2 seconds"
 *      }
 *    }
 * }
 * </pre>
 *
 * <p>Heap configuration properties (used by {@link Heaplet}):
 * <ul>
 *   <li>{@code directory} – path to the directory containing OpenAPI specification files.
 *       Defaults to {@code config/swagger}.</li>
 *   <li>{@code scanInterval} – how often the directory is rescanned for changes, expressed as
 *       a {@link Duration} string (e.g. {@code "30 seconds"}). Defaults to {@code "10 seconds"}.
 *       Set to {@code "0"} to disable periodic rescanning.</li>
 *   <li>{@code defaultHandler} – the downstream {@link Handler} to which matched requests are
 *       forwarded. Optional; if absent, matched requests will result in a NullPointerException
 *       at runtime.</li>
 * </ul>
 */
public class SwaggerRouter implements FileChangeListener, Handler {

    private static final Logger logger = LoggerFactory.getLogger(SwaggerRouter.class);


    /**
     * The upstream handler which should be invoked when no routes match the
     * request.
     */
    private final Handler upstreamHandler;

    /**
     * Map of loaded OpenAPI validators keyed by the specification file name.
     * Access is thread-safe via {@link ConcurrentHashMap}; structural mutations
     * (add/remove) are performed on the {@link FileChangeListener} callbacks.
     */
    Map<String, OpenApiInteractionValidator> validators;

    /**
     * Error codes returned by the OpenAPI validator that indicate the request path or
     * HTTP method is not defined in the specification, as opposed to a constraint violation.
     * When only one of these codes is present the request is silently skipped (not rejected)
     * so that the next loaded specification can be tried.
     */
    final Set<String> MISSING_OPERATION_ERROR_CODES = Set.of("validation.request.path.missing", "validation.request.operation.notAllowed");

    /**
     * Builds a swagger router that loads its configuration from the given directory.
     * @param handler the upstream handler
     */
    public SwaggerRouter(Handler handler) {
        this.upstreamHandler = handler;
        validators = new ConcurrentHashMap<>();
    }


    @Override
    public Promise<Response, NeverThrowsException> handle(Context context, Request request) {

        for(Map.Entry<String, OpenApiInteractionValidator> validatorEntry : validators.entrySet()) {
            OpenApiInteractionValidator validator = validatorEntry.getValue();
            final com.atlassian.oai.validator.model.Request validatorRequest;
            try {
                validatorRequest = validatorRequestOf(request);
            } catch (IOException e) {
                logger.error("exception while reading the request");
                return Promises.newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR));
            }

            ValidationReport requestValidationReport = validator.validateRequest(validatorRequest);
            if(!requestValidationReport.hasErrors()) {
                return upstreamHandler.handle(context, request).then(response -> {
                    final com.atlassian.oai.validator.model.Response validatorResponse;
                    try {
                         validatorResponse = validatorResponseOf(response);
                    } catch (IOException e) {
                        logger.error("exception while reading the response");
                        return new Response(Status.INTERNAL_SERVER_ERROR);
                    }

                    ValidationReport responseValidationReport
                            = validator.validateResponse(validatorRequest.getPath(), validatorRequest.getMethod(), validatorResponse);
                    if(responseValidationReport.hasErrors()) {
                        logger.warn("upstream response does not match specification: {} {}", validatorEntry.getKey(), responseValidationReport);
                        return new Response(Status.BAD_GATEWAY);
                    }
                    return response;
                });
            }

            if(requestValidationReport.getMessages().stream().allMatch(m -> MISSING_OPERATION_ERROR_CODES.contains(m.getKey()))) {
                logger.debug("request does not match given validator: {} {}", validatorEntry.getKey(), requestValidationReport);
            } else {
                logger.warn("client request does not match specification: {} {}", validatorEntry.getKey(), requestValidationReport);
                return Promises.newResultPromise(new Response(Status.BAD_REQUEST));
            }
        }
        logger.info("client request does not match any specified route");
        return Promises.newResultPromise(Responses.newNotFound());
    }

    @Override
    public void onChanges(FileChangeSet changes) {

        for (File file : changes.getRemovedFiles()) {
            try {
                logger.info("removing swagger file: {}", file);
                validators.remove(file.getName());
            } catch (Exception e) {
                logger.error("An error occurred while handling the removed file '{}'", file.getAbsolutePath(), e);
            }
        }

        for (File file : changes.getAddedFiles()) {
            try {
                logger.info("loading swagger file: {}", file);
                validators.put(file.getName(), OpenApiInteractionValidator.createFor(file.getAbsolutePath()).build());
            } catch (Exception e) {
                logger.error("An error occurred while handling the added file '{}'", file.getAbsolutePath(), e);
            }
        }

        for (File file : changes.getModifiedFiles()) {
            try {
                logger.info("loading updated swagger file: {}", file);
                validators.put(file.getName(), OpenApiInteractionValidator.createFor(file.getAbsolutePath()).build());
            } catch (Exception e) {
                logger.error("An error occurred while handling the modified file '{}'", file.getAbsolutePath(), e);
            }
        }
    }

    /**
     * Stops this handler, shutting down and clearing all the managed routes.
     */
    public void stop() {
        validators.clear();
    }

    public static class Heaplet extends GenericHeaplet {

        private SwaggerDirectoryMonitor directoryMonitor;
        private ScheduledFuture<?> scheduledCommand;
        private Duration scanInterval;


        @Override
        public Object create() throws HeapException {
            File directory = config.get("directory").as(evaluatedWithHeapProperties()).as(file());
            if (directory == null) {
                // By default, uses the config/routes from the environment
                Environment env = heap.get(ENVIRONMENT_HEAP_KEY, Environment.class);
                directory = new File(env.getConfigDirectory(), "swagger");
            }
            this.directoryMonitor = new SwaggerDirectoryMonitor(directory);
            this.scanInterval = config.get("scanInterval")
                    .as(evaluatedWithHeapProperties())
                    .defaultTo("10 seconds").as(duration());

            Handler defaultHandler = config.get("handler").as(requiredHeapObject(heap, Handler.class));

            return new SwaggerRouter(defaultHandler);
        }


        @Override
        public void start() throws HeapException {
            Runnable command = () -> {
                try {
                    directoryMonitor.monitor((SwaggerRouter) object);
                } catch (Exception e) {
                    logger.error("An error occurred while scanning the directory", e);
                }
            };

            command.run();

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
                ((SwaggerRouter) object).stop();
            }
            super.destroy();
        }
    }

    private static com.atlassian.oai.validator.model.Request validatorRequestOf(@Nonnull final org.forgerock.http.protocol.Request request) throws IOException {
        SimpleRequest.Builder builder = new SimpleRequest.Builder(request.getMethod(), request.getUri().getPath());
        if(request.getEntity().getBytes().length > 0) {
            builder.withBody(request.getEntity().getBytes());
        }

        if (request.getHeaders() != null) {
            request.getHeaders().asMapOfHeaders().forEach((key, value) -> builder.withHeader(key, value.getValues()));
            if(request.getEntity().getBytes().length > 0
                    && request.getHeaders().keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Content-Type"))) {
                builder.withHeader("Content-Type", "application/json");
            }
        }

        List<NameValuePair> params = URLEncodedUtils.parse(request.getUri().asURI(), StandardCharsets.UTF_8);

        Map<String, List<String>> paramsMap = params.stream()
                .collect(Collectors.groupingBy(
                        NameValuePair::getName,
                        Collectors.mapping(NameValuePair::getValue, Collectors.toList())
                ));
        paramsMap.forEach(builder::withQueryParam);

        return builder.build();
    }

    private static com.atlassian.oai.validator.model.Response validatorResponseOf(@Nonnull final org.forgerock.http.protocol.Response response) throws IOException {
        final SimpleResponse.Builder builder = new SimpleResponse.Builder(response.getStatus().getCode());
        if(response.getEntity().getBytes().length > 0) {
            builder.withBody(response.getEntity().getBytes());
        }

        if (response.getHeaders() != null) {
            response.getHeaders().asMapOfHeaders().forEach((key, value) -> builder.withHeader(key, value.getValues()));
            if(response.getEntity().getBytes().length > 0
                    && response.getHeaders().keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Content-Type"))) {
                builder.withHeader("Content-Type", "application/json");
            }
        }
        return builder.build();
    }
}

