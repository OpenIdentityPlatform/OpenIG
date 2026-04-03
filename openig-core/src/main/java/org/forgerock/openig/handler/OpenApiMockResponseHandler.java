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

package org.forgerock.openig.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link Handler} that generates valid mock HTTP responses with realistic test data
 * derived from an OpenAPI / Swagger specification.
 *
 * <p>Instead of proxying to an upstream service the handler:
 * <ol>
 *   <li>Matches the incoming request path + method against the paths declared in the spec.</li>
 *   <li>Locates the best response schema (prefers 200, then 201, then first 2xx, then
 *       {@code default}).</li>
 *   <li>Recursively generates a JSON body from that schema using {@link MockDataGenerator}.</li>
 *   <li>Returns the generated body with {@code Content-Type: application/json}.</li>
 * </ol>
 *
 * <p>If no matching path is found the handler returns {@code 404 Not Found}; if a path is
 * matched but the HTTP method is not declared the handler returns {@code 405 Method Not Allowed}.
 *
 * <h2>Heap configuration</h2>
 * <pre>{@code
 * {
 *   "name": "MockHandler",
 *   "type": "OpenApiMockResponseHandler",
 *   "config": {
 *     "spec": "${read('/path/to/openapi.yaml')}",
 *     "defaultStatusCode": 200,
 *     "arraySize": 3
 *   }
 * }
 * }</pre>
 *
 * <table border="1">
 *   <caption>Configuration properties</caption>
 *   <tr><th>Property</th><th>Type</th><th>Required</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>spec</td><td>String</td><td>Yes</td><td>–</td>
 *       <td>OpenAPI spec content (YAML or JSON)</td></tr>
 *   <tr><td>defaultStatusCode</td><td>Integer</td><td>No</td><td>200</td>
 *       <td>HTTP status code to use for generated responses</td></tr>
 *   <tr><td>arraySize</td><td>Integer</td><td>No</td><td>1</td>
 *       <td>Number of items to generate for array-typed responses</td></tr>
 * </table>
 */
public class OpenApiMockResponseHandler implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(OpenApiMockResponseHandler.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.registerModule(new JavaTimeModule());
    }

    private final OpenAPI openAPI;

    private final int defaultStatusCode;

    private final int arraySize;

    /**
     * Creates a new mock handler backed by the supplied spec content.
     *
     * @param specContent     the raw OpenAPI spec (YAML or JSON)
     * @param defaultStatusCode HTTP status code to use for generated responses
     * @param arraySize       number of items to generate for array-typed responses
     */
    public OpenApiMockResponseHandler(final String specContent,
                                      final int defaultStatusCode,
                                      final int arraySize) {
        final ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        final SwaggerParseResult result =
                new OpenAPIParser().readContents(specContent, null, options);
        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            logger.warn("OpenAPI spec parse warnings: {}", result.getMessages());
        }
        this.openAPI          = result.getOpenAPI();
        this.defaultStatusCode = defaultStatusCode;
        this.arraySize        = arraySize;
    }

    // Package-private constructor for tests (allows injecting a pre-parsed spec)
    OpenApiMockResponseHandler(final OpenAPI openAPI, final int defaultStatusCode, final int arraySize) {
        this.openAPI           = openAPI;
        this.defaultStatusCode = defaultStatusCode;
        this.arraySize         = arraySize;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        if (openAPI == null || openAPI.getPaths() == null) {
            return Promises.newResultPromise(jsonResponse(Status.valueOf(defaultStatusCode), "{}"));
        }

        final String requestPath   = request.getUri().getPath();
        final String requestMethod = request.getMethod().toUpperCase();

        // Find matching path template
        PathItem matchedPathItem  = null;
        String   matchedTemplate  = null;
        final String basePath = getBasePath(openAPI);
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            final String entryPath = basePath.isEmpty()
                    ? entry.getKey()
                    : basePath.concat(entry.getKey());
            if (pathMatches(entryPath, requestPath)) {
                matchedPathItem  = entry.getValue();
                matchedTemplate  = entry.getKey();
                break;
            }
        }

        if (matchedPathItem == null) {
            logger.debug("No matching path for {}", requestPath);
            return Promises.newResultPromise(new Response(Status.NOT_FOUND));
        }

        // Find operation for method
        final Operation operation = getOperation(matchedPathItem, requestMethod);
        if (operation == null) {
            logger.debug("No operation for {} {}", requestMethod, matchedTemplate);
            return Promises.newResultPromise(new Response(Status.METHOD_NOT_ALLOWED));
        }

        // Resolve best response schema
        final Schema<?> schema = bestResponseSchema(operation);
        final Object body      = generateBody(schema);

        final String json;
        try {
            json = MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialise mock response", e);
            return Promises.newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR));
        }

        return Promises.newResultPromise(jsonResponse(Status.valueOf(defaultStatusCode), json));
    }

    // -----------------------------------------------------------------------
    // Path matching
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if the concrete {@code requestPath} matches the OpenAPI
     * path template (which may contain {@code {paramName}} placeholders).
     */
    static boolean pathMatches(final String template, final String requestPath) {
        if (template == null || requestPath == null) {
            return false;
        }
        // Convert template to regex: escape dots, replace {param} with [^/]+
        final String regex = "^"
                + template.replace(".", "\\.")
                           .replaceAll("\\{[^/{}]+}", "[^/]+")
                + "$";
        return requestPath.matches(regex);
    }

    // -----------------------------------------------------------------------
    // Operation lookup
    // -----------------------------------------------------------------------

    private static Operation getOperation(final PathItem pathItem, final String method) {
        switch (method) {
            case "GET":     return pathItem.getGet();
            case "PUT":     return pathItem.getPut();
            case "POST":    return pathItem.getPost();
            case "DELETE":  return pathItem.getDelete();
            case "OPTIONS": return pathItem.getOptions();
            case "HEAD":    return pathItem.getHead();
            case "PATCH":   return pathItem.getPatch();
            case "TRACE":   return pathItem.getTrace();
            default:        return null;
        }
    }

    // -----------------------------------------------------------------------
    // Response schema resolution
    // -----------------------------------------------------------------------

    /**
     * Picks the best {@link Schema} from the operation's responses.
     * Priority: 200 → 201 → first 2xx → default.
     *
     * @return the schema, or {@code null} if none is declared
     */
    @SuppressWarnings("rawtypes")
    static Schema<?> bestResponseSchema(final Operation operation) {
        final ApiResponses responses = operation.getResponses();
        if (responses == null || responses.isEmpty()) {
            return null;
        }

        // Try status codes in preference order
        for (final String code : new String[]{"200", "201"}) {
            final Schema<?> s = schemaFromResponse(responses.get(code));
            if (s != null) {
                return s;
            }
        }

        // First 2xx
        for (final Map.Entry<String, ApiResponse> entry : responses.entrySet()) {
            if (entry.getKey().startsWith("2")) {
                final Schema<?> s = schemaFromResponse(entry.getValue());
                if (s != null) {
                    return s;
                }
            }
        }

        // default
        return schemaFromResponse(responses.getDefault());
    }

    @SuppressWarnings("rawtypes")
    private static Schema<?> schemaFromResponse(final ApiResponse apiResponse) {
        if (apiResponse == null) {
            return null;
        }
        final Content content = apiResponse.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }
        // Prefer application/json, then first entry
        MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            mediaType = content.values().iterator().next();
        }
        return mediaType == null ? null : mediaType.getSchema();
    }

    private static String getBasePath(OpenAPI spec) {
        if (spec.getServers() == null || spec.getServers().isEmpty()) {
            return "";
        }
        final Server server = spec.getServers().get(0);
        if (server.getUrl() == null || server.getUrl().isBlank()
                || server.getUrl().equals("/")) {
            return "";
        }
        // Remove trailing slash
        String url = server.getUrl().trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        try {
            return new URI(url).getPath();
        } catch (URISyntaxException e) {
            logger.warn("error parsing base URI: {}", e.toString());
            return "";
        }
    }

    // -----------------------------------------------------------------------
    // Body generation
    // -----------------------------------------------------------------------

    /**
     * Generates a Java object graph from the supplied schema that can be serialised to JSON.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    Object generateBody(final Schema<?> schema) {
        return generateValue(null, schema, 0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object generateValue(final String fieldName, final Schema<?> schema, final int depth) {
        if (schema == null) {
            return null;
        }

        // Depth guard to prevent infinite recursion on circular $refs
        if (depth > 10) {
            return null;
        }

        // 1. Use example value if present (highest priority)
        if (schema.getExample() != null) {
            return schema.getExample();
        }

        // 2. Use first enum value if defined
        final List<?> enums = schema.getEnum();
        if (enums != null && !enums.isEmpty()) {
            return enums.get(0);
        }

        // 3. Handle composed schemas (allOf / oneOf / anyOf)
        if (schema instanceof ComposedSchema) {
            final ComposedSchema composed = (ComposedSchema) schema;
            if (composed.getAllOf() != null && !composed.getAllOf().isEmpty()) {
                return generateAllOf(composed.getAllOf(), depth);
            }
            if (composed.getOneOf() != null && !composed.getOneOf().isEmpty()) {
                return generateValue(fieldName, composed.getOneOf().get(0), depth + 1);
            }
            if (composed.getAnyOf() != null && !composed.getAnyOf().isEmpty()) {
                return generateValue(fieldName, composed.getAnyOf().get(0), depth + 1);
            }
        }

        final String type = schema.getType();

        // 4. Object type
        if ("object".equals(type) || (type == null && schema.getProperties() != null)) {
            return generateObject(schema, depth);
        }

        // 5. Array type
        if ("array".equals(type) || schema instanceof ArraySchema) {
            return generateArray(fieldName, schema, depth);
        }

        // 6. Delegate to MockDataGenerator for primitives
        return MockDataGenerator.generate(fieldName, schema);
    }

    /**
     * Merges all schemas from an {@code allOf} list into a single object map.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> generateAllOf(final List<Schema> schemas, final int depth) {
        final Map<String, Object> merged = new LinkedHashMap<>();
        for (final Schema<?> s : schemas) {
            final Object v = generateValue(null, s, depth + 1);
            if (v instanceof Map) {
                merged.putAll((Map<String, Object>) v);
            }
        }
        return merged;
    }

    /**
     * Generates a JSON object (Map) from the schema's properties.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> generateObject(final Schema<?> schema, final int depth) {
        final Map<String, Object> obj = new LinkedHashMap<>();
        final Map<String, Schema> properties = schema.getProperties();
        if (properties == null || properties.isEmpty()) {
            return obj;
        }
        for (final Map.Entry<String, Schema> entry : properties.entrySet()) {
            obj.put(entry.getKey(), generateValue(entry.getKey(), entry.getValue(), depth + 1));
        }
        return obj;
    }

    /**
     * Generates a JSON array from the schema's items sub-schema.
     */
    @SuppressWarnings({"rawtypes"})
    private List<Object> generateArray(final String fieldName, final Schema<?> schema, final int depth) {
        final Schema<?> items = schema instanceof ArraySchema
                ? ((ArraySchema) schema).getItems()
                : schema.getItems();
        final List<Object> list = new ArrayList<>();
        final int count = arraySize > 0 ? arraySize : 1;
        for (int i = 0; i < count; i++) {
            list.add(generateValue(fieldName, items, depth + 1));
        }
        return list;
    }

    // -----------------------------------------------------------------------
    // Response helpers
    // -----------------------------------------------------------------------

    private static Response jsonResponse(final Status status, final String body) {
        final Response response = new Response(status);
        response.getHeaders().put("Content-Type", "application/json");
        response.setEntity(body);
        return response;
    }

    // -----------------------------------------------------------------------
    // Heaplet
    // -----------------------------------------------------------------------

    /**
     * Creates and initialises an {@link OpenApiMockResponseHandler} in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            final JsonValue evaluatedConfig = config.as(evaluatedWithHeapProperties());
            final String spec = evaluatedConfig.get("spec").required().asString();
            final int defaultStatusCode = evaluatedConfig.get("defaultStatusCode").defaultTo(200).asInteger();
            final int arraySize = evaluatedConfig.get("arraySize").defaultTo(1).asInteger();
            return new OpenApiMockResponseHandler(spec, defaultStatusCode, arraySize);
        }
    }
}
