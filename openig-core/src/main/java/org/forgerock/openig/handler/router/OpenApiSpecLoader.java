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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.parser.OpenAPIParser;
import io.swagger.util.ObjectMapperFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Detects whether a file in the routes directory is an OpenAPI spec (Swagger 2.x or OpenAPI 3.x)
 * in either JSON or YAML format, and parses it into an {@link OpenAPI} model.
 *
 * <p>Detection logic:
 * <ol>
 *   <li>File extension must be {@code .json}, {@code .yaml}, or {@code .yml}.</li>
 *   <li>The parsed root object must contain an {@code openapi} key (OAS 3.x) or
 *       a {@code swagger} key (Swagger 2.x).</li>
 * </ol>
 *
 * <p>Normal OpenIG route JSON files contain a {@code handler} or {@code name} root key but
 * not {@code openapi}/{@code swagger}, so they are not matched.
 */
public class OpenApiSpecLoader {

    private static final Logger logger = LoggerFactory.getLogger(OpenApiSpecLoader.class);

    private static final String EXT_JSON = ".json";

    private final static ObjectMapper JSON_MAPPER = ObjectMapperFactory.createJson();
    private final static ObjectMapper YAML_MAPPER = ObjectMapperFactory.createYaml();

    public Optional<OpenAPI> tryLoad(final File file) {
        if (!isOpenApiFile(file)) {
            return Optional.empty();
        }
        try {
            final ParseOptions parseOptions = new ParseOptions();
            parseOptions.setResolve(true);
            parseOptions.setResolveFully(true);

            // Use the file URI so that relative $ref paths are resolved correctly
            final String fileUri = file.toURI().toString();
            final SwaggerParseResult result = new OpenAPIParser().readLocation(fileUri, null, parseOptions);

            if (result == null || result.getOpenAPI() == null) {
                logger.warn("Failed to parse OpenAPI spec from {}: parser returned null", file.getName());
                return Optional.empty();
            }
            if (result.getMessages() != null && !result.getMessages().isEmpty()) {
                result.getMessages().forEach(msg ->
                        logger.warn("OpenAPI parse warning for {}: {}", file.getName(), msg));
                if(result.getMessages().stream().anyMatch(m -> m.toLowerCase().contains("exception"))) {
                    return Optional.empty();
                }
            }
            logger.info("Successfully loaded OpenAPI spec from {}", file.getName());
            return Optional.of(result.getOpenAPI());
        } catch (Exception e) {
            logger.error("Error loading OpenAPI spec from {}: {}", file.getName(), e.getMessage(), e);
            return Optional.empty();
        }
    }


    /**
     * Returns {@code true} if the given file has a supported extension AND its root document
     * contains an {@code openapi} or {@code swagger} key, meaning it looks like an OpenAPI spec
     * rather than a regular OpenIG route configuration.
     *
     * @param file the file to test
     * @return {@code true} if the file appears to be an OpenAPI specification
     */
    public boolean isOpenApiFile(final File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try {
            final JsonNode root = parseRootNode(file);
            if (root == null || !root.isObject()) {
                return false;
            }
            return root.has("openapi") || root.has("swagger");
        } catch (IOException e) {
            logger.debug("Could not probe file {} for OpenAPI markers: {}", file.getName(), e.getMessage());
            return false;
        }
    }

    private JsonNode parseRootNode(final File file) throws IOException {
        final String name = file.getName().toLowerCase();
        if (name.endsWith(EXT_JSON)) {
            return JSON_MAPPER.readTree(file);
        } else {
            return YAML_MAPPER.readTree(file);
        }
    }
}
