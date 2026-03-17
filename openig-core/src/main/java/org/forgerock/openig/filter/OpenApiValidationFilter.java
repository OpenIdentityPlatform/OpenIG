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
import java.util.stream.Collectors;

/**
 * Validates HTTP requests and responses against an
 * OpenAPI (Swagger 2.x / OpenAPI 3.x) specification
 *
 * <h2>Request validation</h2>
 * <p>If the request fails validation the filter returns a {@code 400 Bad Request} response
 * immediately, without forwarding the request downstream.  The response body is a plain-text
 * list of validation messages.
 *
 * <h2>Response validation</h2>
 * <p>After the downstream handler returns a response, the filter validates it against the spec.
 * Behaviour on failure is controlled by the {@code failOnResponseViolation} configuration flag:
 * <ul>
 *   <li>{@code true} – return a {@code 502 Bad Gateway} with the validation messages.</li>
 *   <li>{@code false} (default) – log a warning and pass the response through unchanged.</li>
 * </ul>
 *
 * <h2>Heap configuration</h2>
 * <pre>{@code
 * {
 *   "name": "myValidator",
 *   "type": "OpenApiValidationFilter",
 *   "config": {
 *     "specFile": "/path/to/openapi.yaml",
 *     "failOnResponseViolation": false
 *   }
 * }
 * }</pre>
 */
public class OpenApiValidationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(OpenApiValidationFilter.class);

    private final OpenApiInteractionValidator validator;

    private final boolean failOnResponseViolation;

    /**
     * Creates a filter backed by a pre-built {@link OpenApiInteractionValidator}.
     *
     * @param spec                    The OpenAPI / Swagger specification to use in the validator
     * @param failOnResponseViolation if {@code true}, a response validation failure results in
     *                               a {@code 502} error; if {@code false}, it is only logged
     */
    private OpenApiValidationFilter(String spec, boolean failOnResponseViolation) {
        this(OpenApiInteractionValidator.createForInlineApiSpecification(spec).build(), failOnResponseViolation);
    }

    OpenApiValidationFilter(OpenApiInteractionValidator validator, boolean failOnResponseViolation) {
        this.validator = validator;
        this.failOnResponseViolation = failOnResponseViolation;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {

        final SimpleRequest validatorRequest;
        try {
            validatorRequest = validatorRequestOf(request);
        } catch (IOException e) {
            logger.error("exception while reading the request");
            return Promises.newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR));
        }

        final ValidationReport requestReport = validator.validateRequest(validatorRequest);
        if (requestReport.hasErrors()) {
            logger.info("Request validation failed for {} {}: {}",
                    request.getMethod(), request.getUri(), requestReport);
            return Promises.newResultPromise(
                    buildErrorResponse(Status.BAD_REQUEST, "Request validation failed:\n" + requestReport));
        }

        return next.handle(context, request).then(response -> {
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
                logger.warn("upstream response does not match specification: {}", responseValidationReport);
                if(failOnResponseViolation) {
                    return buildErrorResponse (Status.BAD_GATEWAY, "Response validation failed:\n" + responseValidationReport);
                }
            }
            return response;
        });
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

    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {

            JsonValue evaluatedConfig = config.as(evaluatedWithHeapProperties());
            final String openApiSpec = evaluatedConfig.get("spec").required().asString();

            final boolean failOnResponseViolation =
                    evaluatedConfig.get("failOnResponseViolation").defaultTo(false).asBoolean();

            return new OpenApiValidationFilter(openApiSpec, failOnResponseViolation);

        }
    }
}
