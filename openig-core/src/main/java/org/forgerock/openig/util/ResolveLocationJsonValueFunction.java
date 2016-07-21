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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.util;

import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.JsonValue.set;
import static org.forgerock.json.JsonValueFunctions.url;
import static org.forgerock.openig.util.JsonValues.readJson;

import java.io.IOException;
import java.net.URL;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.util.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This function will resolve the special field {@code $location} from a JsonValue.
 *
 * The rules of transformation are :
 *
 * <pre>
 *     {@code
 * { "$location" : "..." } => [ "a", "b", "c" ]
 *
 * "properties" : { "ns" : { "$location" : "..." } } => "properties" : { "ns" : [ "a", "b", "c" ] }
 *
 * "properties" : { "ns" : [ 1, { "$location" : "..." }, 2 ] } => "properties" : { "ns" : [ 1, [ "a", "b", "c" ], 2 } }
 * </pre>
 *
 */
class ResolveLocationJsonValueFunction implements Function<JsonValue, JsonValue, JsonValueException> {

    private static final Logger logger = LoggerFactory.getLogger(ResolveLocationJsonValueFunction.class);

    @Override
    public JsonValue apply(JsonValue node) {
        // Lookup for the special case first :  is it a sole element ?
        // { "$location" : "...." }
        if (node.isMap() && node.size() == 1 && node.isDefined("$location")) {
            return fetch(node.get("$location"));
        }

        if (node.isMap()) {
            return applyOnMap(node);
        } else if (node.isCollection()) {
            return applyOnCollection(node);
        } else {
            return node;
        }
    }

    private JsonValue applyOnMap(JsonValue node) {
        JsonValue result = json(object());
        for (String key : node.keys()) {
            JsonValue value = node.get(key);
            // { "foo" : { "$location" : "...." } }
            result.put(key, apply(value).getObject());
        }
        return result;
    }

    private JsonValue applyOnCollection(JsonValue node) {
        // [ "foo", { "$location" : "...." }, "quix" ]
        JsonValue result = json(node.isList() ? array() : set());
        for (JsonValue elem : node) {
            result.add(apply(elem).getObject());
        }
        return result;
    }

    private JsonValue fetch(JsonValue location) {
        URL url = location.as(url());
        if (url == null) {
            throw new JsonValueException(location, "Expecting a valid URL");
        }

        logger.trace("Fetching " + url);
        try {
            return readJson(url);
        } catch (IOException e) {
            throw new JsonValueException(location, "An error occurred while reading the JSON from " + url, e);
        }
    }
}

