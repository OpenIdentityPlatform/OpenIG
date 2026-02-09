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

package org.openidentityplatform.openig.filter;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

/**
 * MCPServerFeaturesFilter
 * <br/>
 * This filter enforces allow/deny policies for MCP (Management & Control Protocol)
 * features exchanged as JSON-RPC payloads with an MCP server. It inspects both
 * incoming requests and outgoing responses and removes or rejects features
 * according to the configured rules.
 *
 * <p>Policy enforcement logic:
 * <ul>
 *   <li>Deny lists take precedence over allow lists</li>
 *   <li>Empty allow list means all features are allowed (unless denied)</li>
 *   <li>Non-empty allow list means only listed features are allowed</li>
 *   <li>Denied features are always blocked, regardless of allow list</li>
 * </ul>
 *
 * <pre>
 * {
 *     "type": "MCPFeaturesFilter",
 *     "config": {
 *         "allow": {
 *             "tools": ["get_weather", "tool2"],
 *             "prompts": ["code_review", "prompt2"]
 *         },
 *         "deny": {
 *             "resources": ["file:///project/src/main.rs"],
 *             "resources/templates": ["file:///{path}"]
 *         }
 *     }
 * }
 * </pre>
 */
public class MCPServerFeaturesFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(MCPServerFeaturesFilter.class);

    Map<MCPFeature, List<String>> allowFeatures;
    Map<MCPFeature, List<String>> denyFeatures;

    public Map<MCPFeature, List<String>> getAllowFeatures() {
        return allowFeatures;
    }

    public Map<MCPFeature, List<String>> getDenyFeatures() {
        return denyFeatures;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        JsonValue inputValue;
        try {
            inputValue = json(request.getEntity().getJson());
        } catch (IOException e) {
            logger.debug("Error parsing JSON request body", e);
            return newResponsePromise(new Response(Status.BAD_REQUEST));
        }
        String method = inputValue.get("method").asString();

        JsonValue methodNode = inputValue.get("method");
        if (methodNode == null || methodNode.isNull()) {
            logger.debug("Missing 'method' in JSON-RPC request");
            return newResponsePromise(new Response(Status.BAD_REQUEST));
        }

        try {
            checkFeaturesRequest(method, inputValue);
        } catch (FeatureIsNotAllowedException e) {
            logger.warn("feature {}: {} is not allowed", e.getMcpFeature(), e.getFeatureName());
            Response response = getFeatureDeniedResponse(inputValue, e);
            return newResponsePromise(response);
        }

        return next.handle(context, request)
                .then(response -> {
                    JsonValue outputValue;
                    try {
                        outputValue = json(response.getEntity().getJson());
                    } catch (IOException e) {
                        logger.debug("Error parsing response JSON body", e);
                        return newInternalServerError();
                    }
                    JsonValue result = outputValue.get("result");
                    filterFeaturesResponse(method, result);
                    response.setEntity(outputValue);
                    return response;
                });
    }

    private static Response getFeatureDeniedResponse(JsonValue inputValue, FeatureIsNotAllowedException e) {
        String errMessage = "";
        switch (e.getMcpFeature()){
            case TOOLS:
                errMessage = "Unknown tool: invalid_tool_name";
                break;
            case PROMPTS:
                errMessage = "Unknown prompt: invalid_prompt_name";
                break;
            case RESOURCES:
                errMessage = "Unknown resource: invalid_resource_name";
                break;
            case RESOURCES_TEMPLATES:
                errMessage = "Unknown resource template: invalid_resource_template_name";
                break;
        }
        JsonValue responseEntity = json(object(
                field("jsonrpc", "2.0"),
                field("id", inputValue.get("id")),
                field("error", object(
                        field("code", -32602),
                        field("message", errMessage)
                ))
        ));
        Response response = new Response(Status.OK);
        response.setEntity(responseEntity);
        return response;
    }

    private void checkFeaturesRequest(String method, JsonValue inputValue) throws FeatureIsNotAllowedException {
        MCPFeature feature;
        switch (method) {
            case "tools/call":
                feature = MCPFeature.TOOLS;
                break;
            case "prompts/get":
                feature = MCPFeature.PROMPTS;
                break;
            case "resources/list":
                feature = MCPFeature.RESOURCES;
                break;
            case "resources/templates/list":
                feature = MCPFeature.RESOURCES_TEMPLATES;
                break;
            default:
                return;
        }

        JsonValue queriedFeatureJson = inputValue.get("params").get(feature.idField);
        if(queriedFeatureJson == null || queriedFeatureJson.isNull()) {
            return;
        }

        String queriedFeatureName = queriedFeatureJson.asString();

        if (!isFeatureAllowed(feature, queriedFeatureName)) {
            throw new FeatureIsNotAllowedException(feature, queriedFeatureName);
        }
    }

    private boolean isFeatureAllowed(MCPFeature feature, String featureName) {
        List<String> denied = this.denyFeatures.get(feature);
        if (denied != null && !denied.isEmpty() && denied.contains(featureName)) {
            return false;
        }

        List<String> allowed = this.allowFeatures.get(feature);
        if (allowed != null && !allowed.isEmpty()) {
            return allowed.contains(featureName);
        }
        return true;
    }

    private void filterFeaturesResponse(String method, JsonValue result) {
        MCPFeature feature;
        switch (method) {
            case "tools/list":
                feature = MCPFeature.TOOLS;
                break;
            case "prompts/list":
                feature = MCPFeature.PROMPTS;
                break;
            case "resources/list":
                feature = MCPFeature.RESOURCES;
                break;
            case "resources/templates/list":
                feature = MCPFeature.RESOURCES_TEMPLATES;
                break;
            default:
                return;
        }

        List<JsonValue> returnedFeatures = result.get(feature.name).asList()
                .stream().map(JsonValue::json).collect(Collectors.toList());

        List<JsonValue> filteredReturnedFeatures
                = filterResponseFeature(feature, returnedFeatures,
                    this.allowFeatures.get(feature), this.denyFeatures.get(feature));

        result.put(feature.name, filteredReturnedFeatures);

    }

    /**
     * Filter a list of feature objects.
     *
     * @param featuresList the original feature JSON objects
     * @param allowed      allowed names (empty == no allow constraint)
     * @param denied       denied names (empty == no deny constraint)
     * @return filtered list (new list instance)
     */
    public List<JsonValue> filterResponseFeature(MCPFeature mcpFeature,
                                                 List<JsonValue> featuresList,
                                                 List<String> allowed, List<String> denied) {
        List<JsonValue> result = new ArrayList<>(featuresList);

        if(denied != null && !denied.isEmpty()) {
            result = result.stream()
                    .filter(t -> !denied.contains(t.get(mcpFeature.idField).asString()))
                    .collect(Collectors.toList());
        }

        if(allowed != null && !allowed.isEmpty()) {
            result = featuresList.stream()
                    .filter(t -> allowed.contains(t.get(mcpFeature.idField).asString()))
                    .collect(Collectors.toList());
        }



        return result;
    }

    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            MCPServerFeaturesFilter filter = new MCPServerFeaturesFilter();
            JsonValue evaluatedConfig = config.as(evaluatedWithHeapProperties());
            JsonValue allowConfig = evaluatedConfig.get("allow");
            filter.allowFeatures = Arrays.stream(MCPFeature.values())
                    .collect(Collectors.toUnmodifiableMap(
                            f -> f,
                            f -> Collections.unmodifiableList(allowConfig.get(f.name)
                                    .defaultTo(emptyList()).asList(String.class))
                    ));

            JsonValue denyConfig = evaluatedConfig.get("deny");
            filter.denyFeatures = Arrays.stream(MCPFeature.values())
                    .collect(Collectors.toUnmodifiableMap(
                            f -> f,
                            f -> Collections.unmodifiableList(denyConfig.get(f.name)
                                    .defaultTo(emptyList()).asList(String.class))
                    ));
            return filter;
        }
    }

    public enum MCPFeature {
        TOOLS("tools", "name"),
        PROMPTS("prompts", "name"),
        RESOURCES("resources", "uri"),
        RESOURCES_TEMPLATES("resources/templates", "uriTemplate");

        private final String name;

        private final String idField;
        MCPFeature(String name, String idField) {
            this.name = name;
            this.idField = idField;
        }
    }

    static class FeatureIsNotAllowedException extends Exception {

        private final String featureName;

        private final MCPFeature mcpFeature;

        public FeatureIsNotAllowedException(MCPFeature mcpFeature, String featureName) {
            this.mcpFeature = mcpFeature;
            this.featureName = featureName;
        }

        public MCPFeature getMcpFeature() {
            return mcpFeature;
        }

        public String getFeatureName() {
            return featureName;
        }

    }
}
