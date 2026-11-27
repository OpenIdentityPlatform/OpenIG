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
 * Copyright 2026 3A Systems, LLC.
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status

class ModelDefender {

    List<String> allowedModels;

    ModelDefender(List<String> allowedModels) {
        this.allowedModels = allowedModels
    }

    boolean isModelAllowed(Object openAiRequest) {
        return allowedModels.contains(openAiRequest.model)
    }
}

class SystemPromptDefender {

    String systemPrompt
    SystemPromptDefender(String systemPrompt) {
        this.systemPrompt = systemPrompt
    }

    Object transformRequest(Object openAiRequest) {
        def newSystemMessage = [
            role: "system",
            content: systemPrompt
        ]

        if (openAiRequest.messages instanceof List) {
            openAiRequest.messages.removeAll { it.role == "system" }
            openAiRequest.messages.add(0, newSystemMessage)
        }

        return openAiRequest
    }

}

class MaxInputLengthDefender {
    private long maxLength = 0

    MaxInputLengthDefender(long maxLength) {
        this.maxLength = maxLength
    }

    boolean isMaxInputLengthExceeded(Object openAiRequest) {
        if(maxLength > 0) {
            def userMessage = openAiRequest.messages.collect { it.content }.join()
            if(userMessage.length() > maxLength) {
                return true
            }
        }
        return false
    }
}

class TypoglycemiaDefender {
    private static final Set<String> SENSITIVE_KEYWORDS = [
        "ignore", "previous", "instructions", "system", "prompt", "developer", "bypass"
    ] as Set

    private String normalize(String input) {
        if (!input) return ""

        return input.split(/\s+/).collect { word ->
            String cleanWord = word.replaceAll(/[^\w]/, "").toLowerCase()
            if (cleanWord.size() < 4) return word // Small words rarely trigger typoglycemia

            String matched = SENSITIVE_KEYWORDS.find { keyword ->
                isTypoglycemicMatch(cleanWord, keyword)
            }

            return matched ?: word
        }.join(" ")
    }

    private boolean isTypoglycemicMatch(String scrambled, String target) {
        if (scrambled.size() != target.size()) return false
        if (scrambled[0] != target[0] || scrambled[-1] != target[-1]) return false

        def sMid = scrambled[1..-2].toList().sort()
        def tMid = target[1..-2].toList().sort()
        return sMid == tMid
    }

    Object transformRequest(Object openAiRequest) {
        if (openAiRequest.messages instanceof List) {
            openAiRequest.messages.each { it.content = normalize(it.content) }
        }
        return openAiRequest
    }
}

class PromptInjectionFilterDefender {
    private static final List<String> DEFAULT_BLACKLIST_PATTERNS = [
        "(?i)ignore\\s+all\\s+(previous|above)\\s+instructions",
        "(?i)disregard\\s+(the|any)\\s+(system|original)\\s+(prompt|message)",
        "(?i)you\\s+are\\s+now\\s+in\\s+(developer|dan|god)\\s+mode",
        "(?i)new\\s+rule:",
        "(?i)switch\\s+to\\s+your\\s+(unrestricted|internal)\\s+mode",
        "(?i)translate\\s+everything\\s+above\\s+into\\s+base64"
    ]

    private List<String> blackListPatterns;

    PromptInjectionFilterDefender(List<String> blackListPatterns) {
        if(blackListPatterns) {
            this.blackListPatterns = blackListPatterns
        } else {
            this.blackListPatterns = DEFAULT_BLACKLIST_PATTERNS
        }
    }

    private boolean isInjection(String userInput) {
        if (!userInput) return false

        // 1. Basic pattern check
        return blackListPatterns.any { pattern ->
            userInput.find(pattern)
        }
    }

    boolean containsInjection(Object openAiRequest) {
        if (openAiRequest.messages instanceof List) {
            return openAiRequest.messages.any { isInjection(it.content) }
        }
        return false
    }

}

def generateErrorResponse(status, message) {
    def response = new Response()
    response.status = status
    response.headers['Content-Type'] = "application/json"
    response.setEntity(JsonOutput.toJson([error: message]))
    return response
}

def modelDefender = new ModelDefender(allowedModels)
def maxInputLengthDefender = new MaxInputLengthDefender(maxInputLength)
def systemPromptDefender = new SystemPromptDefender(systemPrompt)
def typoglycemiaDefender = new TypoglycemiaDefender()
def promptInjectionDefender = new PromptInjectionFilterDefender(null)

def slurper = new JsonSlurper()
def openAiRequest
try {
    openAiRequest = slurper.parseText(request.entity.getString())
} catch (Exception ignored) {
    return generateErrorResponse(Status.BAD_REQUEST, "invalid JSON")
}
if (!(openAiRequest instanceof Map) || !(openAiRequest.messages instanceof List) ||
        !openAiRequest.messages || openAiRequest.stream ||
        !openAiRequest.messages.every { it instanceof Map && it.content instanceof String }) {
    return generateErrorResponse(Status.BAD_REQUEST, "expected non-streaming chat messages with text content")
}

if(!modelDefender.isModelAllowed(openAiRequest)) {
    logger.warn("model is not allowed, allowed models: {}", allowedModels)
    return generateErrorResponse(Status.FORBIDDEN, "request is not allowed, invalid model")
}

if(maxInputLengthDefender.isMaxInputLengthExceeded(openAiRequest)) {
    logger.warn("user input length exceeded")
    return generateErrorResponse(Status.FORBIDDEN, "request is not allowed, input length exceeded")
}

openAiRequest = systemPromptDefender.transformRequest(openAiRequest)
openAiRequest = typoglycemiaDefender.transformRequest(openAiRequest)

if(promptInjectionDefender.containsInjection(openAiRequest)) {
    logger.warn("request contains a blocked pattern")
    return generateErrorResponse(Status.FORBIDDEN, "request is not allowed, prompt injection detected")
}

def newEntity = JsonOutput.toJson(openAiRequest)

request.entity.setString(newEntity)
request.headers.remove('Content-Length')

return next.handle(context, request)
