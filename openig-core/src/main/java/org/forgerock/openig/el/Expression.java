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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */

package org.forgerock.openig.el;

import static org.forgerock.openig.el.Bindings.bindings;

import java.beans.FeatureDescriptor;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

import org.forgerock.http.util.Loader;
import org.forgerock.openig.resolver.Resolver;
import org.forgerock.openig.resolver.Resolvers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.odysseus.el.ExpressionFactoryImpl;

/**
 * An Unified Expression Language expression. Creating an expression is the equivalent to
 * compiling it. Once created, an expression can be evaluated within a supplied scope. An
 * expression can safely be evaluated concurrently in multiple threads.
 *
 * @param <T> expected result type
 */
public class Expression<T> {

    private static final Logger logger = LoggerFactory.getLogger(Expression.class);

    /** The underlying EL expression that this object represents. */
    protected final ValueExpression valueExpression;

    /** The original string used to create this expression. */
    private final String original;

    /** The expected type of this expression. */
    private final Class<T> expectedType;

    /** the initial bindings captured when creating this expression. */
    private final Bindings initialBindings;

    /** The expression plugins configured in META-INF/services. */
    private static final Map<String, ExpressionPlugin> PLUGINS =
            Collections.unmodifiableMap(Loader.loadMap(String.class, ExpressionPlugin.class));

    /**
     * Factory method to create an Expression.
     *
     * @param <T> expected result type
     * @param expression The expression to parse.
     * @param expectedType The expected result type of the expression.
     * @return An expression based on the given string.
     * @throws ExpressionException
     *             if the expression was not syntactically correct.
     */
    public static <T> Expression<T> valueOf(String expression, Class<T> expectedType) throws ExpressionException {
        return valueOf(expression, expectedType, bindings());
    }

    /**
     * Factory method to create an Expression.
     *
     * @param <T> expected result type
     * @param expression The expression to parse.
     * @param expectedType The expected result type of the expression.
     * @param initialBindings The initial bindings used when evaluated this expression
     * @return An expression based on the given string.
     * @throws ExpressionException
     *             if the expression was not syntactically correct.
     */
    public static <T> Expression<T> valueOf(String expression, Class<T> expectedType, Bindings initialBindings)
            throws ExpressionException {
        return new Expression<>(expression, expectedType, initialBindings);
    }

    /**
     * Constructs an expression for later evaluation.
     *
     * @param expression the expression to parse.
     * @param expectedType The expected result type of the expression.
     * @param initialBindings The initial bindings used when evaluated this expression
     * @throws ExpressionException if the expression was not syntactically correct.
     */
    protected Expression(String expression, Class<T> expectedType, Bindings initialBindings)
            throws ExpressionException {
        original = expression;
        this.expectedType = expectedType;
        this.initialBindings = initialBindings;
        try {
            ExpressionFactoryImpl exprFactory = new ExpressionFactoryImpl();
            /*
             * We still use Object.class but use the expectedType in the evaluation. If we use the expectedType instead
             * of Object.class at the creation, then we had some breaking changes :
             * - "not a boolean" as Boolean.class => before : null, after : false
             * - "${null}" as String.class => before : null, after : the empty String
             * - accessing a missing bean property as an Integer => before : null, after : 0
             *
             * But note that by still using Object.class prevents from using our own TypeConverter.
             */
            valueExpression = exprFactory.createValueExpression(new XLContext(null), expression, Object.class);
        } catch (ELException ele) {
            throw new ExpressionException(ele);
        }
    }

    /**
     * Evaluates the expression within the specified bindings and returns the resulting object if it matches the
     * specified type, or {@code null} if it does not resolve or match.
     *
     * @param bindings
     *            the bindings to evaluate the expression within.
     * @return the result of the expression evaluation, or {@code null} if it does not resolve or match the type.
     */
    public T eval(final Bindings bindings) {
        return eval(bindings, Collections.emptySet());
    }

    /**
     * Evaluates the expression until it reads a binding reserved for a later evaluation.
     *
     * @param bindings the bindings to evaluate the expression within
     * @param deferredBindings the names of top-level bindings reserved for later evaluation
     * @return the result of the expression evaluation, or {@code null} if it does not resolve or match the type
     * @throws DeferredBindingException if evaluation reads a deferred binding
     */
    public T eval(final Bindings bindings, final Set<String> deferredBindings) {
        return eval(bindings, deferredBindings, null);
    }

    /**
     * Evaluates the expression with a policy for reading resolved values.
     *
     * @param bindings the bindings to evaluate the expression within
     * @param deferredBindings the names of top-level bindings reserved for later evaluation
     * @param readPolicy the policy for each property or method read
     * @return the result of the expression evaluation, or {@code null} if it does not resolve or match the type
     * @throws DeferredBindingException if evaluation reads a deferred binding or the policy defers a read
     */
    public T eval(final Bindings bindings, final Set<String> deferredBindings,
                  final ReadPolicy readPolicy) {
        Object value;
        try {
            Bindings evaluationBindings = bindings().bind(initialBindings).bind(bindings);
            value = valueExpression.getValue(new XLContext(evaluationBindings.asMap(), deferredBindings, readPolicy));
        } catch (ELException ele) {
            logger.warn("An error occurred while evaluating the expression {}",
                         valueExpression.getExpressionString(),
                         ele);
            // unresolved element yields null value
            value = null;
        }

        if (value == null) {
            return null;
        }

        if (!expectedType.isInstance(value)) {
            logger.info("For the expression {}, expected a result of type {} but got {}. Returning null",
                        valueExpression.getExpressionString(),
                        expectedType.getClass().getName(),
                        value.getClass().getName());
            return null;
        }

        return expectedType.cast(value);
    }

    /**
     * Convenient method to eval an Expression that does not need a scope.
     * @return the result of the expression evaluation, or {@code null} if it does not resolve or match the type.
     */
    public T eval() {
        return eval(bindings());
    }

    /**
     * Returns whether this expression consists entirely of literal text.
     * @return {@code true} for literal text
     */
    public boolean isLiteralText() {
        return valueExpression.isLiteralText();
    }

    /** Controls which values are available in an evaluation. */
    public interface ReadPolicy {
        /**
         * Checks a property read or a method invocation.
         * @param base object being read, or {@code null} for a top-level binding
         * @param member property or method name
         * @param method whether the read is a method invocation
         * @return {@code true} to resolve the member in this evaluation
         */
        boolean canRead(Object base, Object member, boolean method);

        /**
         * Observes a resolved value without changing it.
         * @param base object being read, or {@code null} for a top-level binding
         * @param member property or method name
         * @param value resolved value, or {@code null} when unavailable
         */
        void onRead(Object base, Object member, Object value);
    }

    /** Indicates that evaluation needs a binding reserved for later. */
    public static final class DeferredBindingException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private DeferredBindingException(String name) {
            super(name);
        }
    }

    static class XLContext extends ELContext {
        private final ELResolver elResolver;

        XLContext(Object scope) {
            this(scope, Collections.emptySet(), null);
        }

        private XLContext(Object scope, Set<String> deferredBindings, ReadPolicy readPolicy) {
            elResolver = new XLResolver(scope, deferredBindings, readPolicy);
        }

        @Override
        public ELResolver getELResolver() {
            return elResolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return MethodsMapper.INSTANCE;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }
    }

    private static class XLResolver extends ELResolver {
        private static final BeanELResolver RESOLVER = new BeanELResolver(true);
        private final Object scope;
        private final Set<String> deferredBindings;
        private final ReadPolicy readPolicy;

        XLResolver(final Object scope, final Set<String> deferredBindings, final ReadPolicy readPolicy) {
            // Resolvers.get() don't support null value
            this.scope = (scope == null) ? new Object() : scope;
            this.deferredBindings = deferredBindings;
            this.readPolicy = readPolicy;
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            context.setPropertyResolved(true);
            checkRead(base, property, false);

            if (base == null) {
                String name = property.toString();
                // deal with readonly implicit objects
                ExpressionPlugin node = Expression.PLUGINS.get(name);
                if (node != null) {
                    return observedValue(null, property, node.getObject());
                }
                if (deferredBindings.contains(name)) {
                    throw new DeferredBindingException(name);
                }
            }

            Object value = Resolvers.get((base == null ? scope : base), property);
            return observedValue(base, property, value != Resolver.UNRESOLVED ? value : null);
        }

        private void checkRead(Object base, Object member, boolean method) {
            if (readPolicy != null && !readPolicy.canRead(base, member, method)) {
                throw new DeferredBindingException(member.toString());
            }
        }

        private Object observedValue(Object base, Object member, Object value) {
            if (readPolicy != null) {
                readPolicy.onRead(base, member, value);
            }
            return value;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            context.setPropertyResolved(true);
            return Object.class;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
            context.setPropertyResolved(true);
            Resolvers.put((base == null ? scope : base), property, value);
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            context.setPropertyResolved(true);
            // attempts to write to read-only values are merely ignored
            return false;
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
            return null;
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return (base == null ? String.class : Object.class);
        }

        @Override
        public Object invoke(final ELContext context,
                             final Object base,
                             final Object method,
                             final Class<?>[] paramTypes,
                             final Object[] params) {
            checkRead(base, method, true);
            return observedValue(base, method, RESOLVER.invoke(context, base, method, paramTypes, params));
        }
    }

    /**
     * Returns the original string used to create this expression, unmodified.
     * <p>
     * Note to implementors: That returned value must be usable in
     * Expression.valueOf() to create an equivalent Expression(somehow cloning
     * this instance)
     * </p>
     *
     * @return The original string used to create this expression.
     */
    @Override
    public String toString() {
        return original;
    }
}
