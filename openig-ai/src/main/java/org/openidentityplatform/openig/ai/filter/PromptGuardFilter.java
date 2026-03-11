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

package org.openidentityplatform.openig.ai.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;


/**
 * Detects and blocks prompt-injection attacks
 * before they reach the downstream LLM API.
 *
 * <h2>Detection pipeline</h2>
 * <ol>
 *   <li><strong>Prompt extraction</strong> – parses the JSON request body and
 *       extracts all prompt text from {@code messages[].content} (OpenAI chat
 *       format) or a top-level {@code prompt} field.</li>
 *   <li><strong>Layer-1: Regex</strong> – fast, deterministic pattern matching
 *       including Unicode normalization and Base64 decode-then-scan.</li>
 *   <li><li><strong>Layer-2: Typoglycemia</strong> (enabled by
 *      {@code typoglycemiaEnabled}, default {@code true}) – catches injection
 *      keywords whose interior letters have been transposed to evade exact
 *      matching (e.g. {@code "jialbrek"} for {@code "jailbreak"}). Uses a
 *      fingerprint gate (first char + last char + sorted interior bag) followed
 *      by true unrestricted Damerau-Levenshtein distance ≤
 *      {@code typoglycemiaMaxEditDist} (default 3).</li>
 * </ol>
 *
 * <h2>Actions on detection</h2>
 * <ul>
 *   <li>{@code BLOCK}    – returns a configurable HTTP error (default 400).</li>
 *   <li>{@code LOG_ONLY} – no headers, no blocking.</li>
 * </ul>
 *
 * <h2>Route JSON configuration</h2>
 * <pre>{@code
 * {
 *   "type": "PromptGuardFilter",
 *   "config": {
 *      "action":                       "BLOCK",
 *      "patternFile":                  "injection-patterns.json",
 *      "typoglycemiaEnabled":           true,
 *      "typoglycemiaMaxEditDist":       3,
 *      "typoglycemiaMinWordLen":        4,
 *      "typoglycemiaKeywords":         "typoglycemia-keywords.json",
 *      "blockResponse": {
 *          "status": 400,
 *          "body":   "{ \"error\": \"prompt_injection_detected\" }"
 *     }
 *   }
 * }
 * }</pre>
 */
public class PromptGuardFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(MCPServerFeaturesFilter.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    private final InjectionDetector detector;

    private final Action  action;

    public PromptGuardFilter(InjectionDetector detector, Action action) {
        this.detector = detector;
        this.action = action;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        String promptText;
        try {
            promptText = extractPromptText(request);
        } catch (IOException e) {
            logger.warn("Failed to read/parse request body: {}", e.getMessage());
            return Promises.newResultPromise(buildBlockResponse("request_parse_error"));
        }

        if (promptText == null || promptText.isBlank()) {
            logger.debug("No prompt text found — passing through");
            return next.handle(context, request);
        }

        DetectionResult result = detector.scan(promptText);

        if (!result.isInjection()) {
            return next.handle(context, request);
        }

        logger.warn("Injection detected: detector={} reason={} score={}",
                result.getDetector(), result.getReason(), result.getScore());

        if (Action.BLOCK.equals(action)) {
            return Promises.newResultPromise(buildBlockResponse(result.getReason()));
        }

        return next.handle(context, request);
    }

    /**
     * Extracts all user/system prompt text from the LLM API request body
     * Supports OpenAI chat completions format: {@code { "messages": [{ "content": "..." }] }}
     */
    static String extractPromptText(Request request) throws IOException {


        List<String> parts = new LinkedList<>();

        JsonValue jsonBody = json(request.getEntity().getJson());

        JsonValue messages = jsonBody.get("messages");
        for (Object msg : messages.asList()) {
            JsonValue content = json(msg).get("content");
            if (content.isString()) {
                parts.add(content.asString());
            } else if (content.isList()) {
                for (Object block : content.asList()) {
                    JsonValue text = json(block).get("text");
                    if (text.isString()) {
                        parts.add(text.asString());
                    }
                }
            }
        }

        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private Response buildBlockResponse(String reason) {
        Response response = new Response(Status.BAD_REQUEST);
        response.getHeaders().put("Content-Type", "application/json");
        response.getHeaders().put("X-Blocked-Reason", reason);
        response.setEntity(json(object(
                field("error", "prompt_injection_detected"),
                field("reason", reason)
        )));
        return response;
    }

    public enum Action { BLOCK, LOG_ONLY }

    public static final class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {

            String  actionStr     = config.get("action").defaultTo("BLOCK").asString();
            String  patternFile   = config.get("patternFile")
                    .defaultTo(this.getClass().getClassLoader()
                            .getResource("injection-patterns.json").toString()).asString();

            Action action;
            try {
                action = Action.valueOf(actionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new HeapException("Invalid action '" + actionStr + "'; must be BLOCK, or LOG_ONLY");
            }

            List<RegexDetector.PatternEntry> patterns = loadJsonListFromUrl(patternFile, new TypeReference<>() {});
            RegexDetector regexDetector = new RegexDetector(patterns);

            // Typoglycemia config
            boolean typoEnabled   = config.get("typoglycemiaEnabled").defaultTo(true).asBoolean();
            int typoMaxEdit   = config.get("typoglycemiaMaxEditDist").defaultTo(2).asInteger();
            int typoMinLen    = config.get("typoglycemiaMinWordLen").defaultTo(4).asInteger();
            String typoglycemiaKeywords = config.get("typoglycemiaKeywords").defaultTo(this.getClass().getClassLoader()
                    .getResource("typoglycemia-keywords.json").toString()).asString();

            List<InjectionDetector> chain = new ArrayList<>();
            chain.add(regexDetector);

            if (typoEnabled) {
                List<String> typoKeywords = loadJsonListFromUrl(typoglycemiaKeywords, new TypeReference<>() {});
                TypoglycemiaDetector typoDetector = new TypoglycemiaDetector(typoMinLen, typoMaxEdit, typoKeywords);
                chain.add(typoDetector);
            }
            InjectionDetector composite = new CompositeDetector(chain);
            return new PromptGuardFilter(composite, action);
        }


        private <T> List<T> loadJsonListFromUrl(
                String urlString,
                TypeReference<List<T>> typeRef) {

            if (urlString == null || urlString.isBlank()) {
                logger.warn("URL is empty/null - using fallback");
                return List.of();
            }

            try (InputStream is = new URL(urlString).openStream()) {
                if (is == null) {
                    logger.info("file '{}' not found - using fallback", urlString);
                    return List.of();
                }

                List<T> items = mapper.readValue(is, typeRef);
                logger.info("Loaded {} from '{}'", items.size(), urlString);
                return items;

            } catch (IOException e) {
                logger.warn("Failed to load file '{}': {} - using fallback",
                        urlString, e.getMessage());
                return List.of();
            }
        }
    }

}
