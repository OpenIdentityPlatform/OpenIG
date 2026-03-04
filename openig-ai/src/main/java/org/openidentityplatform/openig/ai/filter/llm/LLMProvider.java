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

package org.openidentityplatform.openig.ai.filter.llm;

/**
 * LLM providers whose APIs can be proxied by {@link org.openidentityplatform.openig.ai.filter.LLMProxyFilter}.
 */
public enum LLMProvider {

    OPENAI("https://api.openai.com/v1", "Authorization", "Bearer"),

    ANTHROPIC("https://api.anthropic.com/v1", "x-api-key", ""),

    MISTRAL("https://api.mistral.ai/v1", "Authorization", "Bearer"),

    AZURE_OPENAI("", "api-key", ""),

    OPENAI_COMPATIBLE("", "Authorization", "Bearer");

    private final String defaultBaseUrl;

    private final String authHeaderName;

    private final String authScheme;

    LLMProvider(String defaultBaseUrl, String authHeaderName, String authScheme) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.authHeaderName = authHeaderName;
        this.authScheme = authScheme;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public String buildAuthHeaderValue(String apiKey) {
        if (authScheme == null || authScheme.isEmpty()) {
            return apiKey;
        }
        return authScheme + " " + apiKey;
    }
}