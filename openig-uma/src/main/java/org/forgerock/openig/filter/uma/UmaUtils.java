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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.filter.uma;

import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;

import org.forgerock.http.protocol.Entity;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;

/**
 * UMA toolkit.
 */
public final class UmaUtils {

    private UmaUtils() { }

    /**
     * Parse the response's content as a JSON structure.
     *
     * @param entity
     *         Response's content
     * @return {@link JsonValue} representing the JSON content
     * @throws OAuth2TokenException
     *         if there was some errors during parsing (not JSON)
     */
    public static JsonValue asJson(final Entity entity) throws OAuth2TokenException {
        try {
            return new JsonValue(entity.getJson());
        } catch (IOException e) {
            // Do not use Entity.toString(), we probably don't want to fully output the content here
            throw new OAuth2TokenException("Cannot read response content as JSON", e);
        } finally {
            // Sure ? this is removing any content
            closeSilently(entity);
        }
    }
}
