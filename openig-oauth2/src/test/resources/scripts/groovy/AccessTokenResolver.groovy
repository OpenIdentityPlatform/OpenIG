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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2025 3A Systems LLC.
 */

package scripts.groovy

import org.forgerock.http.oauth2.AccessTokenInfo
import org.forgerock.json.JsonValue
import static org.forgerock.json.JsonValue.*

logger.info("" + config.tokenInfoEndpoint)

def rawJson = new JsonValue(object(
        field("name", "John"),
        field("lasname", "Doe"))
)
def at = new AccessTokenInfo(rawJson, token, new HashSet<>(), System.currentTimeMillis() + 100000)
return at