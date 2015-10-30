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
package org.forgerock.openig.el;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Utility class for evaluating expression in some collections.
 */
public final class Expressions {

    private Expressions() {
        // Prevent from instantiating.
    }

    /**
     * Evaluate a map that may contain some values that needs to be evaluated.
     *
     * @param map the map to evaluate
     * @param bindings the bindings used for the evaluation
     * @return a new Map containing the result of the evaluation.
     * @throws ExpressionException if an error occurs while evaluating the expression
     */
    public static Map<String, Object> evaluate(Map<String, Object> map, Bindings bindings) throws ExpressionException {
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), evaluate(entry.getValue(), bindings));
        }
        return result;
    }

    /**
     * Evaluate a list that may contain some String that needs to be evaluated.
     *
     * @param list the list to evaluate
     * @param bindings the bindings used for the evaluation
     * @return a new list containing the results of the evaluations
     * @throws ExpressionException if an error occurs while evaluating the expression
     */
    public static List<Object> evaluate(List<Object> list, Bindings bindings) throws ExpressionException {
        List<Object> evaluatedList = new ArrayList<>();
        for (Object object : list) {
            evaluatedList.add(evaluate(object, bindings));
        }
        return evaluatedList;
    }

    /**
     * Evaluate a String.
     *
     * @param value the String to evaluate
     * @param bindings the bindings used for the evaluation
     * @return the result of the evaluation.
     * @throws ExpressionException if an error occurs while evaluating the expression
     */
    public static Object evaluate(String value, Bindings bindings) throws ExpressionException {
        return Expression.valueOf(value, Object.class).eval(bindings);
    }

    /**
     * Evaluate an Object that may contain some String that needs to be evaluated. The supported types of Object are :
     * String, List and Map.
     *
     * @param value the String to evaluate
     * @param bindings the bindings used for the evaluation
     * @return the result of the evaluation.
     * @throws ExpressionException if an error occurs while evaluating the expression
     */
    @SuppressWarnings("unchecked")
    public static Object evaluate(Object value, Bindings bindings) throws ExpressionException {
        if (value instanceof String) {
            return evaluate((String) value, bindings);
        } else if (value instanceof List) {
            return evaluate((List) value, bindings);
        } else if (value instanceof Map) {
            return evaluate((Map) value, bindings);
        } else {
            return value;
        }
    }
}
