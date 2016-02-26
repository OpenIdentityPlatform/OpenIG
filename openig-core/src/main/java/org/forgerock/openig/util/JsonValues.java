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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.util;

import static java.lang.String.*;
import static java.util.Collections.*;
import static org.forgerock.http.util.Loader.*;
import static org.forgerock.util.time.Duration.duration;

import java.util.List;

import org.forgerock.json.JsonException;
import org.forgerock.json.JsonTransformer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.alias.ClassAliasResolver;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.log.Logger;
import org.forgerock.util.Function;
import org.forgerock.util.Utils;
import org.forgerock.util.time.Duration;

/**
 * Provides additional functionality to {@link JsonValue}.
 */
public final class JsonValues {

    /** List of alias service providers found at initialization time. */
    private static final List<ClassAliasResolver> CLASS_ALIAS_RESOLVERS =
            unmodifiableList(loadList(ClassAliasResolver.class));

    private static final Function<JsonValue, Expression<String>, HeapException> OF_EXPRESSION =
            new Function<JsonValue, Expression<String>, HeapException>() {
                @Override
                public Expression<String> apply(final JsonValue value) throws HeapException {
                    return asExpression(value, String.class);
                }
            };

    /**
     * Private constructor for utility class.
     */
    private JsonValues() { }

    /**
     * Resolves a String-based {@link JsonValue} instance that may contain an {@link Expression}.
     */
    private static final JsonTransformer EXPRESSION_TRANSFORMER = new JsonTransformer() {
        @Override
        public void transform(final JsonValue value) {
            if (value.isString()) {
                try {
                    Expression<Object> expression = Expression.valueOf(value.asString(), Object.class);
                    value.setObject(expression.eval());
                } catch (ExpressionException e) {
                    throw new JsonException(format("Expression '%s' (in %s) is not syntactically correct",
                                                   value.asString(),
                                                   value.getPointer()), e);
                }
            }
        }
    };

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
        } catch (ExceptionInInitializerError | InstantiationException | IllegalAccessException e) {
            throw new JsonValueException(value, e);
        }
    }

    /**
     * Evaluate a {@link JsonValue} node as an integer : if this is already an integer, it just returns it, but if this
     * is a string, then it evaluates it as an {@link Expression}, whose expected type is integer.
     * @param node the node to evaluate
     * @return the {@link Integer} coming from the node's value if it's an integer or resulting from the evaluation of
     * the {@link Expression}
     * @throws JsonValueException if the {@link JsonValue} is neither an integer nor a string
     */
    public static Integer asInteger(JsonValue node) {
        return evaluateJsonStaticExpression(node).asInteger();
    }

    /**
     * Evaluate a {@link JsonValue} node as a string : if this is a string then it evaluates it as an
     * {@link Expression}.
     * @param node the node to evaluate
     * @return the {@link String} resulting from the evaluation of the {@link Expression}
     * @throws JsonValueException if the {@link JsonValue} is neither a {@link String} nor a valid {@link String} typed
     * expression.
     */
    public static String asString(JsonValue node) {
        return evaluateJsonStaticExpression(node).asString();
    }

    /**
     * Evaluate a {@link JsonValue} node as a duration : if this is a string then it evaluates it as an
     * {@link Expression}, whose expected type is a {@link String} parseable for a {@link Duration}.
     * @param node the node to evaluate
     * @return the {@link Duration} resulting from the evaluation of the {@link Expression}
     * @throws JsonValueException if the {@link JsonValue} is neither a {@link String} nor a valid {@link Duration}
     * typed expression.
     */
    public static Duration asDuration(JsonValue node) {
        String value = asString(node);
        try {
            return value == null ? null : duration(value);
        } catch (IllegalArgumentException iae) {
            throw new JsonValueException(node, value + " is not a valid duration");
        }
    }

    /**
     * Evaluate a {@link JsonValue} node as a boolean : if this is already a boolean, it just returns it, but if this
     * is a string, then it evaluates it as an {@link Expression}, whose expected type is boolean.
     * @param node the node to evaluate
     * @return the {@link Boolean} coming from the node's value if it's a boolean or resulting from the evaluation of
     * the {@link Expression}
     * @throws JsonValueException if the {@link JsonValue} is neither a boolean nor a string
     */
    public static Boolean asBoolean(JsonValue node) {
        return evaluateJsonStaticExpression(node).asBoolean();
    }

    /**
     * Returns a JSON value string value as an expression. If the value is {@code null}, this
     * method returns {@code null}.
     *
     * @param <T> expected result type
     * @param value the JSON value containing the expression string.
     * @param expectedType The expected result type of the expression.
     * @return the expression represented by the string value.
     * @throws JsonValueException if the value is not a string or the value is not a valid expression.
     */
    public static <T> Expression<T> asExpression(JsonValue value, Class<T> expectedType) {
        try {
            return (value == null || value.isNull() ? null : Expression.valueOf(value.asString(), expectedType));
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
        Expression<String> expression = asExpression(value, String.class);
        if (expression != null) {
            return expression.eval();
        }
        return null;
    }

    /**
     * Evaluates the given JSON value object, applying a {@link JsonTransformer}
     * that will evaluate all String nodes. Transformation is applied
     * recursively. <p>Malformed expressions are ignored e.g: <tt>"$$$${{"</tt>
     * and their values are not changed. <p>When an error occurs during the
     * evaluation of an expression, the value is set to {@code null} because we
     * cannot differentiate successful evaluations or failed ones.
     *
     * @param value
     *            The JSON value object to evaluate.
     * @param logger
     *            The logger which should be used for warnings.
     * @return A JSON value object which all expressions string are evaluated.
     * @throws JsonException
     *             If the value not a valid expression.
     */
    public static JsonValue evaluate(final JsonValue value, final Logger logger) {
        return new JsonValue(value.getObject(), singleton(new JsonTransformer() {
            @Override
            public void transform(final JsonValue value) {
                if (value.isString()) {
                    try {
                        // Malformed expressions are ignored
                        final Expression<Object> expression = asExpression(value, Object.class);
                        if (expression != null) {
                            final Object evaluated = expression.eval();
                            // Errors during evaluation are represented with a null result
                            if (evaluated == null) {
                                logger.warning(format("The expression '%s' (in %s) cannot be evaluated",
                                                      expression, value.getPointer()));
                            }
                            // We should only replace the value for successful evaluations,
                            // but we cannot differentiate successful evaluations or failed ones
                            value.setObject(evaluated);

                        }
                    } catch (JsonValueException jve) {
                        logger.warning(format("The value %s (in %s) is a malformed expression",
                                              value.asString(), value.getPointer()));
                    }
                }
            }
        }));
    }

    /**
     * Evaluates the given JSON value using an Expression and wraps the returned value as a new JsonValue. This only
     * change value of String types JsonValues, other types are ignored. This mechanism only perform change on the given
     * JsonValue object (child nodes are left unchanged).
     *
     * @param value
     *         the JSON value to be evaluated.
     * @return a new JsonValue instance containing the resolved expression (or the original wrapped value if it was not
     * changed)
     * @throws JsonException
     *         if the expression cannot be evaluated (syntax error or resolution error).
     */
    public static JsonValue evaluateJsonStaticExpression(final JsonValue value) {
        // Returned a transformed, deep object copy
        return new JsonValue(value, singleton(EXPRESSION_TRANSFORMER));
    }

    /**
     * Returns, if the given JSON value contains one of the names, the first
     * defined JSON value, otherwise if the given JSON value does not match any
     * of the names, then a JsonValue encapsulating null is returned.
     * Example of use:
     *
     * <pre>{@code
     * Uri uri = firstOf(config, "authorizeEndpoint", "authorize_endpoint").required().asURI();
     * }</pre>
     *
     * @param config
     *            The JSON value where one of the selected names can be found.
     *            Usually in a heaplet configuration for example.
     * @param names
     *            Names of the attributes that you are looking for.
     * @return the specified item JSON value or JsonValue encapsulating null if
     *         none were found.
     */
    public static JsonValue firstOf(final JsonValue config, final String... names) {
        if (names != null) {
            for (final String name : names) {
                if (config.isDefined(name)) {
                    return config.get(name);
                }
            }
        }
        return new JsonValue(null);
    }

    /**
     * Returns a function for transforming JsonValues to expressions.
     *
     * @return A function for transforming JsonValues to expressions.
     */
    public static Function<JsonValue, Expression<String>, HeapException> ofExpression() {
        return OF_EXPRESSION;
    }

    /**
     * Returns a {@link Function} to transform a list of String-based {@link JsonValue}s into a list of required heap
     * objects.
     *
     * @param heap
     *         the heap to query for references resolution
     * @param type
     *         expected object type
     * @param <T>
     *         expected object type
     * @return a {@link Function} to transform a list of String-based {@link JsonValue}s into a list of required heap
     * objects.
     */
    public static <T> Function<JsonValue, T, HeapException> ofRequiredHeapObject(final Heap heap,
                                                                                 final Class<T> type) {
        return new Function<JsonValue, T, HeapException>() {
            @Override
            public T apply(final JsonValue value) throws HeapException {
                return heap.resolve(value, type);
            }
        };
    }

    /**
     * Returns a {@link Function} to transform a list of String-based {@link JsonValue}s into a list of enum
     * constant values of the given type.
     *
     * @param enumType expected type of the enum
     * @param <T> Enumeration type
     * @return a {@link Function} to transform a list of String-based {@link JsonValue}s into a list of enum
     * constant values of the given type.
     */
    public static <T extends Enum<T>> Function<JsonValue, T, HeapException> ofEnum(final Class<T> enumType) {
        return new Function<JsonValue, T, HeapException>() {
            @Override
            public T apply(final JsonValue value) throws HeapException {
                return Utils.asEnum(value.asString(), enumType);
            }
        };
    }

    /**
     * Returns the named property from the provided JSON object, falling back to
     * zero or more deprecated property names. This method will log a warning if
     * only a deprecated property is found or if two equivalent property names
     * are found.
     *
     * @param config
     *            The configuration object.
     * @param logger
     *            The logger which should be used for deprecation warnings.
     * @param name
     *            The non-deprecated property name.
     * @param deprecatedNames
     *            The deprecated property names ordered from newest to oldest.
     * @return The request property.
     */
    public static JsonValue getWithDeprecation(JsonValue config, Logger logger, String name,
            String... deprecatedNames) {
        String found = config.isDefined(name) ? name : null;
        for (String deprecatedName : deprecatedNames) {
            if (config.isDefined(deprecatedName)) {
                if (found == null) {
                    found = deprecatedName;
                    warnForDeprecation(config, logger, name, found);
                } else {
                    logger.warning("Cannot use both '" + deprecatedName + "' and '" + found
                            + "' attributes, " + "will use configuration from '" + found
                            + "' attribute");
                    break;
                }
            }
        }
        return found == null ? config.get(name) : config.get(found);
    }

    /**
     * Issues a warning that the configuration property {@code oldName} is
     * deprecated and that the property {@code newName} should be used instead.
     *
     * @param config
     *            The configuration object.
     * @param logger
     *            The logger which should be used for deprecation warnings.
     * @param name
     *            The non-deprecated property name.
     * @param deprecatedName
     *            The deprecated property name.
     */
    public static void warnForDeprecation(final JsonValue config, final Logger logger,
            final String name, final String deprecatedName) {
        logger.warning(format("[%s] The '%s' attribute is deprecated, please use '%s' instead",
                config.getPointer(), deprecatedName, name));
    }
}
