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

package org.forgerock.openig.filter;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.forgerock.openig.util.JsonValues.optionalHeapObject;

/**
 * Validates HTTP requests and responses against an
 * OpenAPI (Swagger 2.x / OpenAPI 3.x) specification
 *
 * <h2>Request validation</h2>
 * <p>If the request fails validation the filter stops processing and delegates to
 * {@code requestValidationErrorHandler} instead of forwarding the request downstream.
 * The default {@code requestValidationErrorHandler} returns {@code 400 Bad Request}.</p>
 *
 * <h2>Response validation</h2>
 * <p>After the downstream handler returns a response, the filter validates it against the spec.
 * Behaviour depends on {@code failOnResponseViolation}:
 * <ul>
 *   <li>{@code true} – delegate to {@code responseValidationErrorHandler}.  The default returns
 *       {@code 503 Service Unavailable}</li>
 *   <li>{@code false} (default) – log a warning and pass the original response through.</li>
 * </ul>
 * </p>
 *
 * <h2>Heap configuration</h2>
 * <pre>{@code
 * {
 *   "name": "myValidator",
 *   "type": "OpenApiValidationFilter",
 *   "config": {
 *     "specFile": "/path/to/openapi.yaml",
 *     "failOnResponseViolation": false,
 *     "requestValidationErrorHandler": "403BadRequest",
 *     "responseValidationErrorHandler": "503ServiceUnavailable"
 *   }
 * }
 * }</pre>
 */
public class OpenApiValidationFilter implements Filter {

    /**
     * Key under which the {@link ValidationReport} is stored in the
     * {@link AttributesContext} before delegating to an error handler.
     */
    public static final String ATTR_OPENAPI_VALIDATION_REPORT = "openApiValidationReport";

    private static final Logger logger = LoggerFactory.getLogger(OpenApiValidationFilter.class);

    private final OpenApiInteractionValidator validator;

    private final boolean failOnResponseViolation;

    private final Handler requestValidationErrorHandler;

    private final Handler responseValidationErrorHandler;

    /**
     * Creates a filter backed by a pre-built {@link OpenApiInteractionValidator}.
     *
     * @param spec                    The OpenAPI / Swagger specification to use in the validator
     * @param failOnResponseViolation if {@code true}, a response validation failure results in
     *                               a {@code 503} error; if {@code false}, it is only logged
     * @param requestValidationErrorHandler       handler invoked on request validation failure
     * @param responseValidationErrorHandler       handler invoked on response validation failure when
     *                                {@code failOnResponseViolation} is {@code true}
     */
    private OpenApiValidationFilter(String spec, boolean failOnResponseViolation,
                                    Handler requestValidationErrorHandler, Handler responseValidationErrorHandler) {
        this(OpenApiInteractionValidator.createForInlineApiSpecification(spec).build(), failOnResponseViolation,
                requestValidationErrorHandler, responseValidationErrorHandler);
    }

    OpenApiValidationFilter(OpenApiInteractionValidator validator, boolean failOnResponseViolation) {
        this(validator, failOnResponseViolation,
                defaultRequestValidationErrorHandler(), defaultResponseValidationErrorHandler());
    }

    OpenApiValidationFilter(OpenApiInteractionValidator validator, boolean failOnResponseViolation,
                            Handler requestValidationErrorHandler, Handler responseValidationErrorHandler) {
        this.validator = validator;
        this.failOnResponseViolation = failOnResponseViolation;
        this.requestValidationErrorHandler = requestValidationErrorHandler;
        this.responseValidationErrorHandler = responseValidationErrorHandler;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {

        final SimpleRequest validatorRequest;
        try {
            validatorRequest = validatorRequestOf(request);
        } catch (IOException e) {
            logger.error("exception while reading the request", e);
            return Promises.newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR));
        }

        final ValidationReport requestReport = validator.validateRequest(validatorRequest);
        if (requestReport.hasErrors()) {

            logger.info("Request validation failed for {} {}: {}",
                    request.getMethod(), request.getUri(), requestReport);
            return requestValidationErrorHandler.handle(injectReportToContext(context, requestReport), request);
        }

        return next.handle(context, request).then(response -> {
            final com.atlassian.oai.validator.model.Response validatorResponse;
            try {
                validatorResponse = validatorResponseOf(response);
            } catch (IOException e) {
                logger.error("exception while reading the response", e);
                return new Response(Status.INTERNAL_SERVER_ERROR);
            }

            ValidationReport responseValidationReport
                    = validator.validateResponse(validatorRequest.getPath(), validatorRequest.getMethod(), validatorResponse);
            if(responseValidationReport.hasErrors()) {
                logger.warn("upstream response does not match specification: {}", responseValidationReport);
                if(failOnResponseViolation) {
                    try {
                        return responseValidationErrorHandler.handle(
                                injectReportToContext(context, responseValidationReport), request)
                                .get();
                    } catch (InterruptedException | ExecutionException e) {
                        logger.error("exception while handling the response", e);
                        return new Response(Status.INTERNAL_SERVER_ERROR);
                    }
                }
            }
            return response;
        });
    }

    private static Context injectReportToContext(final Context parent, final ValidationReport report) {
        Context context = parent;
        if(!parent.containsContext(AttributesContext.class)) {
            context = new AttributesContext(parent);
        }
        context.asContext(AttributesContext.class).getAttributes().put(ATTR_OPENAPI_VALIDATION_REPORT, report.getMessages());
        return context;
    }

    private static Response buildErrorResponse(final Status status, final String body) {
        final Response response = new Response(status);
        response.getHeaders().put("Content-Type", "text/plain; charset=UTF-8");
        response.setEntity(body);
        return response;
    }

    private static SimpleRequest validatorRequestOf(final Request request) throws IOException {
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

    private static SimpleResponse validatorResponseOf(final Response response) throws IOException {
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

    public static Handler defaultRequestValidationErrorHandler() {
        return (context, request) ->
                Promises.newResultPromise(buildErrorResponse(Status.BAD_REQUEST,
                        "Request validation failed: " + context.asContext(AttributesContext.class)
                                .getAttributes().get(ATTR_OPENAPI_VALIDATION_REPORT).toString()));
    }

    public static Handler defaultResponseValidationErrorHandler() {
        return (context, request) ->
                Promises.newResultPromise(buildErrorResponse(Status.SERVICE_UNAVAILABLE,
                        "Response validation failed: " + context.asContext(AttributesContext.class)
                                .getAttributes().get(ATTR_OPENAPI_VALIDATION_REPORT).toString()));
    }

    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {

            JsonValue evaluatedConfig = config.as(evaluatedWithHeapProperties());
            final String openApiSpec = evaluatedConfig.get("spec").required().asString();

            final boolean failOnResponseViolation =
                    evaluatedConfig.get("failOnResponseViolation").defaultTo(false).asBoolean();

            Handler requestValidationErrorHandler = config.get("requestValidationErrorHandler")
                    .as(optionalHeapObject(heap, Handler.class));
            requestValidationErrorHandler = requestValidationErrorHandler == null ? defaultRequestValidationErrorHandler() : requestValidationErrorHandler;

            Handler responseValidationErrorHandler = config.get("responseValidationErrorHandler")
                    .as(optionalHeapObject(heap, Handler.class));
            responseValidationErrorHandler = responseValidationErrorHandler == null ? defaultResponseValidationErrorHandler() : responseValidationErrorHandler;

            return new OpenApiValidationFilter(openApiSpec, failOnResponseViolation,
                    requestValidationErrorHandler, responseValidationErrorHandler);

        }
    }
}
