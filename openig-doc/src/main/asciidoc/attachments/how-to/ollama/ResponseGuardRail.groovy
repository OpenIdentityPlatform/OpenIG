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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.owasp.esapi.ESAPI

String sanitize(String content) {
    if (removeCodeBlocks) {
        content = content.replaceAll(/(?s)```[^\r\n]*\R.*?```/, "[CODE BLOCK REMOVED]")
    }
    return escapeHtml ? ESAPI.encoder().encodeForHTML(content) : content
}

return next.handle(context, request).then { response ->
    if (response.status.successful) {
        def result = new JsonSlurper().parseText(response.entity.getString())
        result.choices.each { it.message.content = sanitize(it.message.content) }
        response.entity.setString(JsonOutput.toJson(result))
        response.headers.remove("Content-Length")
    }
    return response
}
