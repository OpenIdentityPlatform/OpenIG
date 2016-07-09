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

import static java.lang.String.format;
import static java.util.Collections.unmodifiableList;
import static org.forgerock.http.util.Loader.loadList;

import java.util.List;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.alias.ClassAliasResolver;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.log.Logger;
import org.forgerock.util.Function;

/**
 * Provides additional functionality to {@link JsonValue}.
 */
public final class JsonValues {

    /** List of alias service providers found at initialization time. */
    private static final List<ClassAliasResolver> CLASS_ALIAS_RESOLVERS =
            unmodifiableList(loadList(ClassAliasResolver.class));

    /**
     * Private constructor for utility class.
     */
    private JsonValues() { }

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
     * Returns a function that will look for the name of the object. If it's a reference to an already defined heap
     * object, then that name is returned, if it is an inline declaration, it looks for the name of the object if any
     * and returns it otherwise it returns the pointer to that declaration.
     *
     * @return the name of the heap object or the pointer to that object if it is anonymous.
     */
    public static Function<JsonValue, String, JsonValueException> heapObjectNameOrPointer() {
        return new HeapObjectNameOrPointerJsonTransformFunction();
    }

    /**
     * Returns a function that will evaluate all String nodes. Transformation is applied
     * recursively. <p>Malformed expressions are ignored e.g: <tt>"$$$${{"</tt>
     * and their values are not changed. <p>When an error occurs during the
     * evaluation of an expression, the value is set to {@code null} because we
     * cannot differentiate successful evaluations or failed ones.
     *
     * @return a function to evaluate String nodes of a {@link JsonValue}
     */
    public static Function<JsonValue, JsonValue, JsonValueException> evaluated() {
        return new ExpressionJsonTransformFunction();
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
     * @param <T> expected result type
     * @param type The expected result type of the expression.
     * @return A function for transforming JsonValues to expressions.
     */
    public static <T> Function<JsonValue, Expression<T>, JsonValueException> expression(final Class<T> type) {
        return new Function<JsonValue, Expression<T>, JsonValueException>() {
            @Override
            public Expression<T> apply(final JsonValue value) {
                try {
                    return (value == null || value.isNull() ? null : Expression.valueOf(value.asString(), type));
                } catch (ExpressionException ee) {
                    throw new JsonValueException(value, ee);
                }
            }
        };
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
    public static <T> Function<JsonValue, T, HeapException> requiredHeapObject(final Heap heap, final Class<T> type) {
        return new Function<JsonValue, T, HeapException>() {
            @Override
            public T apply(final JsonValue value) throws HeapException {
                return heap.resolve(value, type);
            }
        };
    }

    /**
     * Returns a {@link Function} to transform a list of String-based {@link JsonValue}s into a list of optional heap
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
    public static <T> Function<JsonValue, T, HeapException> optionalHeapObject(final Heap heap, final Class<T> type) {
        return new Function<JsonValue, T, HeapException>() {
            @Override
            public T apply(final JsonValue value) throws HeapException {
                return heap.resolve(value, type, true);
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
