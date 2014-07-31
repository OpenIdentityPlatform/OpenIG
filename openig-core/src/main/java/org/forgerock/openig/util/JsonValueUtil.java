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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.util;

import static java.util.Collections.unmodifiableList;
import static org.forgerock.openig.util.Loader.loadList;

import java.util.List;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.alias.ClassAliasResolver;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.promise.Function;

/**
 * Provides additional functionality to JsonValue.
 */
public final class JsonValueUtil {

    /**
     * List of alias service providers found at initialisation time.
     */
    private static final List<ClassAliasResolver> CLASS_ALIAS_RESOLVERS =
            unmodifiableList(loadList(ClassAliasResolver.class));

    private static final Function<JsonValue, Expression, HeapException> OF_EXPRESSION =
            new Function<JsonValue, Expression, HeapException>() {
                @Override
                public Expression apply(final JsonValue value) throws HeapException {
                    return asExpression(value);
                }
            };

    /**
     * Private constructor for utility class.
     */
    private JsonValueUtil() { }

    /**
     * TODO: Description.
     *
     * @param value TODO.
     * @return TODO.
     * @throws JsonValueException if value is not a string or the named class could not be found.
     */
    private static Class<?> classForName(JsonValue value) {
        String name = value.asString();
        // Looks for registered aliases first
        Class<?> type = resolveAlias(name);
        if (type != null) {
            return type;
        }
        // No alias found, consider the value as a fully qualified class name
        try {
            return Class.forName(name, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException cnfe) {
            throw new JsonValueException(value, cnfe);
        }
    }

    /**
     * Resolve a given alias against the known aliases service providers.
     * The first {@literal non-null} resolved type is returned.
     */
    private static Class<?> resolveAlias(final String alias) {
        for (ClassAliasResolver service : CLASS_ALIAS_RESOLVERS) {
            Class<?> type = service.resolve(alias);
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    /**
     * Returns the class object associated with a named class or interface, using the thread
     * context class loader. If the value is {@code null}, this method returns {@code null}.
     *
     * @param value the value containing the class name string.
     * @return the class object with the specified name.
     * @throws JsonValueException if value is not a string or the named class could not be found.
     */
    public static Class<?> asClass(JsonValue value) {
        return (value == null || value.isNull() ? null : classForName(value));
    }

    /**
     * Creates a new instance of a named class. The class is instantiated as if by a
     * {@code new} expression with an empty argument list. The class is initialized if it has
     * not already been initialized. If the value is {@code null}, this method returns
     * {@code null}.
     *
     * @param <T> the type of the new instance.
     * @param value the value containing the class name string.
     * @param type the type that the instantiated class should to resolve to.
     * @return a new instance of the requested class.
     * @throws JsonValueException if the requested class could not be instantiated.
     */
    @SuppressWarnings("unchecked")
    public static <T> T asNewInstance(JsonValue value, Class<T> type) {
        if (value == null || value.isNull()) {
            return null;
        }
        Class<?> c = asClass(value);
        if (!type.isAssignableFrom(c)) {
            throw new JsonValueException(value, "expecting " + type.getName());
        }
        try {
            return (T) c.newInstance();
        } catch (ExceptionInInitializerError eiie) {
            throw new JsonValueException(value, eiie);
        } catch (IllegalAccessException iae) {
            throw new JsonValueException(value, iae);
        } catch (InstantiationException ie) {
            throw new JsonValueException(value, ie);
        }
    }

    /**
     * Returns a JSON value string value as an expression. If the value is {@code null}, this
     * method returns {@code null}.
     *
     * @param value the JSON value containing the expression string.
     * @return the expression represented by the string value.
     * @throws JsonValueException if the value is not a string or the value is not a valid expression.
     */
    public static Expression asExpression(JsonValue value) {
        try {
            return (value == null || value.isNull() ? null : new Expression(value.asString()));
        } catch (ExpressionException ee) {
            throw new JsonValueException(value, ee);
        }
    }

    /**
     * Evaluates the given JSON value string as an {@link Expression}.
     *
     * @param value
     *         the JSON value containing the expression string.
     * @return the String that resulted of the Expression evaluation.
     * @throws JsonValueException
     *         if the value is not a string or the value is not a valid string typed expression.
     */
    public static String evaluate(JsonValue value) {
        Expression expression = asExpression(value);
        if (expression != null) {
            return expression.eval(null, String.class);
        }
        return null;
    }

    /**
     * Returns a function for transforming JsonValues to expressions.
     *
     * @return A function for transforming JsonValues to expressions.
     */
    public static final Function<JsonValue, Expression, HeapException> ofExpression() {
        return OF_EXPRESSION;
    }
}
