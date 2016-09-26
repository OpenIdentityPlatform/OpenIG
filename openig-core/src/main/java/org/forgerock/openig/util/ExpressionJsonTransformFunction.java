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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.util.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link Function} that tries to evaluate each {@link String} leaf as an {@link Expression}.
 */
class ExpressionJsonTransformFunction implements Function<JsonValue, JsonValue, JsonValueException> {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionJsonTransformFunction.class);

    private final Bindings bindings;

    /**
     * Creates a new {@link ExpressionJsonTransformFunction} with no bindings.
     */
    public ExpressionJsonTransformFunction() {
        this(Bindings.bindings());
    }

    /**
     * Creates a new {@link ExpressionJsonTransformFunction} with the given bindings used to evaluate the expressions;
     * it does not log anything.
     * @param bindings The bindings used for the evaluation
     */
    public ExpressionJsonTransformFunction(Bindings bindings) {
        this.bindings = bindings;
    }

    @Override
    public JsonValue apply(JsonValue value) {
        Object object;
        if (value.isNull()) {
            object = null;
        } else if (value.isString()) {
            object = transformString(value);
        } else if (value.isNumber()) {
            object = transformNumber(value);
        } else if (value.isList()) {
            object = transformList(value);
        } else if (value.isMap()) {
            object = transformMap(value);
        } else {
            object = value.getObject();
        }

        return new JsonValue(object, value.getPointer());
    }

    private Object transformString(JsonValue value) {
        String str = value.asString();
        try {
            return Expression.valueOf(str, Object.class).eval(bindings);
        } catch (ExpressionException e) {
            logger.warn("Ignoring the malformed expression : {}", str, e);
            // Malformed expressions are ignored
        }
        return str;
    }

    private Number transformNumber(JsonValue value) {
        // Since EL expressions resolve arithmetic expression as Long, to be consistent,
        // let's upgrade all Integers to Long
        // This a mandatory to keep the uniqueness of every element in a Set JsonValue
        Number number = value.asNumber();
        if (number instanceof Integer) {
            return value.asLong();
        }
        return number;
    }

    private Object transformList(JsonValue value) {
        return fillCollection(value, new ArrayList<>(value.size()));
    }

    private Collection<Object> fillCollection(JsonValue value, Collection<Object> result) {
        for (JsonValue elem : value) {
            result.add(apply(elem).getObject());
        }
        return result;
    }

    private Object transformMap(JsonValue value) {
        Map<String, Object> result = new LinkedHashMap<>(value.size());
        for (String key : value.keys()) {
            result.put(key, apply(value.get(key)).getObject());
        }
        return result;
    }

}

