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

package org.forgerock.openig.handler.router;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.servers.Server;
import org.forgerock.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.forgerock.json.JsonValue.json;

/**
 * Converts a parsed {@link OpenAPI} model into an OpenIG route {@link JsonValue}.
 *
 * <p>The generated route:
 * <ul>
 *   <li>Has a {@code name} derived from {@code info.title} (slugified) or the spec filename stem.</li>
 *   <li>Has a {@code condition} expression built from every {@code (path, method)} pair declared
 *       in the spec.  Path parameter placeholders such as {@code {id}} are converted to the
 *       regex {@code [^/]+}, and each clause also checks {@code request.method} against the
 *       HTTP verbs declared for that path.</li>
 *   <li>Has a {@code heap} containing a single {@code OpenApiValidationFilter} heap object
 *       pointing at the spec file.</li>
 *   <li>Has a {@code handler} that is a {@code Chain} with the validation filter followed by a
 *       {@code ClientHandler} that proxies to the first server URL declared in the spec.</li>
 * </ul>
 *
 * <p>The generated route uses the {@code baseURI} decorator if a server URL is found in the spec
 * so that all requests are forwarded to the upstream service.
 */
public class OpenApiRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(OpenApiRouteBuilder.class);

    /** Name used to reference the validation filter inside the heap. */
    private static final String VALIDATOR_HEAP_NAME = "OpenApiValidator";

    /**
     * Builds an OpenIG route {@link JsonValue} for the supplied OpenAPI specification.
     *
     * @param spec     the parsed OpenAPI model
     * @param specFile the original spec file on disk (used for the validator config and as a
     *                 fallback route name)
     * @param failOnResponseViolation if {@code true}, the generated
     *                                {@code OpenApiValidationFilter} will return
     *                                {@code 502 Bad Gateway} when a response violates the spec;
     *                                if {@code false} (default), violations are only logged
     * @return a {@link JsonValue} that can be passed directly to the {@code RouterHandler}'s
     *         internal route-loading mechanism
     */

    public JsonValue buildRouteJson(final OpenAPI spec, final File specFile, boolean failOnResponseViolation) {
        final String routeName = deriveRouteName(spec, specFile);
        final String condition = buildConditionExpression(spec);
        final String baseUri   = extractBaseUri(spec);

        logger.info("Building OpenAPI route '{}' from spec file '{}' (condition: {}, baseUri: {}, failOnResponseViolation: {})",
                routeName, specFile.getName(), condition, baseUri != null ? baseUri : "<none>", failOnResponseViolation);


        // ----- heap: one OpenApiValidationFilter entry -----
        final Map<String, Object> validatorConfig = new LinkedHashMap<>();
        validatorConfig.put("spec", "${read('" + specFile.getAbsolutePath() + "')}");
        validatorConfig.put("failOnResponseViolation", failOnResponseViolation);

        final Map<String, Object> validatorHeapObject = new LinkedHashMap<>();
        validatorHeapObject.put("name", VALIDATOR_HEAP_NAME);
        validatorHeapObject.put("type", "OpenApiValidationFilter");
        validatorHeapObject.put("config", validatorConfig);

        // ----- handler: Chain -> [OpenApiValidationFilter] -> ClientHandler -----
        final Map<String, Object> chainConfig = new LinkedHashMap<>();
        chainConfig.put("filters", List.of(VALIDATOR_HEAP_NAME));
        chainConfig.put("handler", "ClientHandler");

        final Map<String, Object> handlerObject = new LinkedHashMap<>();
        handlerObject.put("type", "Chain");
        handlerObject.put("config", chainConfig);

        // ----- assemble root route object -----
        final Map<String, Object> routeMap = new LinkedHashMap<>();
        routeMap.put("name", routeName);

        if (condition != null) {
            routeMap.put("condition", condition);
        }

        // Apply baseURI decorator when the spec declares a server URL
        if (baseUri != null) {
            routeMap.put("baseURI", baseUri);
        }

        routeMap.put("heap", List.of(validatorHeapObject));
        routeMap.put("handler", handlerObject);

        return json(routeMap);
    }

    /**
     * Produces a URL-safe route name from {@code info.title}, falling back to the filename stem.
     */
    private String deriveRouteName(final OpenAPI spec, final File specFile) {
        String title = null;
        if (spec.getInfo() != null && spec.getInfo().getTitle() != null) {
            title = spec.getInfo().getTitle().trim();
        }
        if (title == null || title.isEmpty()) {
            // Use the filename without extension
            final String fileName = specFile.getName();
            final int dot = fileName.lastIndexOf('.');
            title = dot > 0 ? fileName.substring(0, dot) : fileName;
        }
        // Slugify: lowercase, replace non-alphanumeric (except hyphen) with hyphen, collapse runs
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    private String buildConditionExpression(final OpenAPI spec) {
        if (spec.getPaths() == null || spec.getPaths().isEmpty()) {
            return null; // catch-all
        }


        String baseUri = extractBaseUri(spec);
        final String basePath;
        if (baseUri != null) {
            try {
                basePath = new URI(baseUri).getPath();
            } catch (URISyntaxException e) {
                logger.warn("error parsing base URI: {}", e.toString());
                return null;
            }
        } else {
            basePath = "";
        }

        final List<String> clauses = new ArrayList<>();

        spec.getPaths().forEach((rawPath, pathItem) -> {
            if (pathItem == null) {
                return;
            }
            final String pathRegex = pathToRegex(basePath.concat(rawPath));
            final Set<String> methods = extractMethods(pathItem);

            if (methods.isEmpty()) {
                // Path is declared but has no operations — match the path regardless of method
                clauses.add("matches(request.uri.path, '" + pathRegex + "')");
            } else {
                for (final String method : methods) {
                    clauses.add(
                            "(matches(request.uri.path, '" + pathRegex + "')"
                                    + " && matches(request.method, '^" + method + "$'))");
                }
            }
        });

        if (clauses.isEmpty()) {
            return null;
        }
        if (clauses.size() == 1) {
            return "${" + clauses.get(0) + "}";
        }
        // Multi-clause: wrap each on its own line for readability, joined with ||
        return "${" + String.join("\n    || ", clauses) + "}";
    }

    /**
     * Converts an OpenAPI path template to an anchored Java regex string.
     *
     * <p>Transformation rules (applied in order):
     * <ol>
     *   <li>Literal {@code .} → {@code \.} (escape regex metachar)</li>
     *   <li>Literal {@code +} → {@code \+} (escape regex metachar)</li>
     *   <li>{@code {paramName}} → {@code [^/]+} (path parameter → non-slash segment)</li>
     *   <li>Prepend {@code ^}, append {@code $} (full-path anchor)</li>
     * </ol>
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code /pets}           → {@code ^/pets$}</li>
     *   <li>{@code /pets/{id}}      → {@code ^/pets/[^/]+$}</li>
     *   <li>{@code /a.b/{x}/c}     → {@code ^/a\.b/[^/]+/c$}</li>
     *   <li>{@code /v1/{org}/{repo}/releases} → {@code ^/v1/[^/]+/[^/]+/releases$}</li>
     * </ul>
     *
     * @param openApiPath the raw OpenAPI path template, e.g. {@code /pets/{petId}}
     * @return an anchored regex string suitable for use inside {@code matches()} EL calls
     */
    static String pathToRegex(final String openApiPath) {
        if (openApiPath == null || openApiPath.isEmpty()) {
            return "^/$";
        }
        String regex = openApiPath;
        // 1. Escape literal regex metacharacters that can appear in paths
        regex = regex.replace(".", "\\.");
        regex = regex.replace("+", "\\+");
        // 2. Replace every {paramName} placeholder with a non-slash segment matcher
        regex = regex.replaceAll("\\{[^/{}]+}", "[^/]+");
        // 3. Anchor
        return "^" + regex + "$";
    }

    /**
     * Returns the set of HTTP method names (uppercase) for which the given {@link PathItem}
     * has an operation defined. The set preserves insertion order.
     *
     * <p>Only the standard HTTP verbs recognised by the OpenAPI 3.x spec are considered:
     * GET, PUT, POST, DELETE, OPTIONS, HEAD, PATCH, TRACE.
     *
     * @param pathItem an OpenAPI path item
     * @return ordered set of method names, e.g. {@code ["GET", "POST"]}
     */
    static Set<String> extractMethods(final PathItem pathItem) {
        final Set<String> methods = new LinkedHashSet<>();
        if (pathItem.getGet()     != null) methods.add("GET");
        if (pathItem.getPut()     != null) methods.add("PUT");
        if (pathItem.getPost()    != null) methods.add("POST");
        if (pathItem.getDelete()  != null) methods.add("DELETE");
        if (pathItem.getOptions() != null) methods.add("OPTIONS");
        if (pathItem.getHead()    != null) methods.add("HEAD");
        if (pathItem.getPatch()   != null) methods.add("PATCH");
        if (pathItem.getTrace()   != null) methods.add("TRACE");
        return methods;
    }

    /**
     * Returns the first server URL from the spec (trimming trailing slashes), or {@code null}
     * if no servers are declared.
     */
    private String extractBaseUri(final OpenAPI spec) {
        if (spec.getServers() == null || spec.getServers().isEmpty()) {
            return null;
        }
        final Server server = spec.getServers().get(0);
        if (server.getUrl() == null || server.getUrl().isBlank()
                || server.getUrl().equals("/")) {
            return null;
        }
        // Remove trailing slash
        String url = server.getUrl().trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

}
