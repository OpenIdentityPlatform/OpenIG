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
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.el;

import java.beans.FeatureDescriptor;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

import org.forgerock.openig.resolver.Resolver;
import org.forgerock.openig.resolver.Resolvers;
import org.forgerock.openig.util.Loader;

import de.odysseus.el.ExpressionFactoryImpl;

/**
 * An Unified Expression Language expression. Creating an expression is the equivalent to
 * compiling it. Once created, an expression can be evaluated within a supplied scope. An
 * expression can safely be evaluated concurrently in multiple threads.
 *
 * @param <T> expected result type
 */
public final class Expression<T> {

    /** The underlying EL expression that this object represents. */
    private final ValueExpression valueExpression;

    /** The original string used to create this expression. */
    private final String original;

    /** The expected type of this expression. */
    private Class<T> expectedType;

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
    public static final <T> Expression<T> valueOf(String expression, Class<T> expectedType) throws ExpressionException {
        return new Expression<T>(expression, expectedType);
    }

    /**
     * Constructs an expression for later evaluation.
     *
     * @param expression the expression to parse.
     * @param expectedType The expected result type of the expression.
     * @throws ExpressionException if the expression was not syntactically correct.
     */
    private Expression(String expression, Class<T> expectedType) throws ExpressionException {
        original = expression;
        this.expectedType = expectedType;
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
     * Evaluates the expression within the specified scope and returns the resulting object if it matches the specified
     * type, or {@code null} if it does not resolve or match.
     *
     * @param scope
     *            the scope to evaluate the expression within.
     * @return the result of the expression evaluation, or {@code null} if it does not resolve or match the type.
     */
    public T eval(final Object scope) {
        try {
            Object value = valueExpression.getValue(new XLContext(scope));
            return (value != null && expectedType.isInstance(value) ? expectedType.cast(value) : null);
        } catch (ELException ele) {
            // unresolved element yields null value
            return null;
        }

    }

    /**
     * Convenient method to eval an Expression that does not need a scope.
     * @return the result of the expression evaluation, or {@code null} if it does not resolve or match the type.
     */
    public T eval() {
        return eval(null);
    }

    /**
     * Sets the result of an evaluated expression to a specified value. The expression is
     * treated as an <em>lvalue</em>, the expression resolves to an object whose value will be
     * set. If the expression does not resolve to an object or cannot otherwise be written to
     * (e.g. read-only), then this method will have no effect.
     *
     * @param scope the scope to evaluate the expression within.
     * @param value the value to set in the result of the expression evaluation.
     */
    public void set(Object scope, Object value) {
        try {
            valueExpression.setValue(new XLContext(scope), value);
        } catch (ELException ele) {
            // unresolved elements are simply ignored
        }
    }

    private static class XLContext extends ELContext {
        private final ELResolver elResolver;
        private final FunctionMapper fnMapper = new Functions();

        public XLContext(Object scope) {
            elResolver = new XLResolver(scope);
        }

        @Override
        public ELResolver getELResolver() {
            return elResolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return fnMapper;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }
    }

    private static class XLResolver extends ELResolver {
        private final Object scope;

        public XLResolver(final Object scope) {
            // Resolvers.get() don't support null value
            this.scope = (scope == null) ? new Object() : scope;
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            context.setPropertyResolved(true);

            // deal with readonly implicit objects
            if (base == null) {
                ExpressionPlugin node = Expression.PLUGINS.get(property.toString());
                if (node != null) {
                    return node.getObject();
                }
            }

            Object value = Resolvers.get((base == null ? scope : base), property);
            return (value != Resolver.UNRESOLVED ? value : null);
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
