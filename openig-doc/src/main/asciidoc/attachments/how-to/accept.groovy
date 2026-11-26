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

import com.google.common.net.MediaType
import org.apache.http.message.BasicHeaderValueParser
import org.forgerock.http.header.ContentTypeHeader
import org.forgerock.http.header.MalformedHeaderException
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import org.forgerock.util.Function

boolean accepts(List<String> fields, ContentTypeHeader contentType) {
    if (fields == null || contentType == null) {
        return true
    }
    def representation
    try {
        if (contentType.type == null) {
            throw new IllegalArgumentException('Missing media type')
        }
        representation = MediaType.parse(contentType.type)
        if (contentType.charset != null) {
            representation = representation.withCharset(contentType.charset)
        }
        if (contentType.boundary != null) {
            representation = representation.withParameter(
                    'boundary', contentType.boundary)
        }
        for (parameter in contentType.additionalParameters) {
            if (parameter.value == null) {
                throw new IllegalArgumentException('Missing parameter value')
            }
            representation = representation.withParameter(
                    parameter.key, parameter.value)
        }
    } catch (IllegalArgumentException ignored) {
        // An unparseable media type or an unknown charset is left unchanged.
        return true
    }
    def qualityPattern = ~/(?:0(?:\.[0-9]{0,3})?|1(?:\.0{0,3})?)/
    int bestType = -1
    int bestParameters = -1
    BigDecimal bestQuality = BigDecimal.ZERO
    for (element in BasicHeaderValueParser.parseElements(fields.join(','), null)) {
        try {
            if (element.value != null) {
                throw new IllegalArgumentException('Invalid media range')
            }
            def range = MediaType.parse(element.name)
            BigDecimal quality = BigDecimal.ONE
            boolean qualitySeen = false
            for (parameter in element.parameters) {
                if (parameter.value == null) {
                    throw new IllegalArgumentException('Missing parameter value')
                }
                if (parameter.name.equalsIgnoreCase('q')) {
                    if (qualitySeen || !(parameter.value ==~ qualityPattern)) {
                        throw new IllegalArgumentException('Invalid quality')
                    }
                    qualitySeen = true
                    quality = new BigDecimal(parameter.value)
                } else {
                    range = range.withParameter(parameter.name, parameter.value)
                }
            }
            if (!representation.is(range)) {
                continue
            }
            int type = range.type() == '*' ? 0 : (range.subtype() == '*' ? 1 : 2)
            int parameters = range.parameters().size()
            if (type > bestType ||
                    (type == bestType && parameters > bestParameters)) {
                bestType = type
                bestParameters = parameters
                bestQuality = quality
            } else if (type == bestType && parameters == bestParameters) {
                bestQuality = bestQuality.max(quality)
            }
        } catch (IllegalArgumentException ignored) {
            // A malformed range does not make a representation acceptable.
        }
    }
    return bestQuality > 0
}

return next.handle(context, request).then({ response ->
    ContentTypeHeader contentType
    try {
        contentType = response.headers.get(ContentTypeHeader)
    } catch (MalformedHeaderException ignored) {
        return response
    }
    if (accepts(request.headers['Accept']?.values, contentType)) {
        return response
    }
    response.close()
    def rejected = new Response(Status.NOT_ACCEPTABLE)
    rejected.headers['Content-Type'] = 'application/json'
    rejected.setEntity('{"error":"Not Acceptable"}')
    return rejected
} as Function)
