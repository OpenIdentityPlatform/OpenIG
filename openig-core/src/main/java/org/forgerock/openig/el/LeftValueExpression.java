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

package org.forgerock.openig.el;

import javax.el.ELException;

import org.forgerock.util.Reject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link LeftValueExpression} is a specialized {@link Expression} to which we can assign a value.
 *
 * @param <T> expected result type
 */
public final class LeftValueExpression<T> extends Expression<T> {

    private static final Logger logger = LoggerFactory.getLogger(LeftValueExpression.class);

    /**
     * Factory method to create a LeftValueExpression.
     *
     * @param <T> expected result type
     * @param expression The expression to parse.
     * @param expectedType The expected result type of the expression.
     * @return An expression based on the given string.
     * @throws ExpressionException
     *             if the expression was not syntactically correct or not a left-value expression.
     */
    public static <T> LeftValueExpression<T> valueOf(String expression, Class<T> expectedType)
            throws ExpressionException {
        return new LeftValueExpression<>(expression, expectedType);
    }

    /**
     * Constructs an expression for later evaluation.
     *
     * @param expression
     *         the expression to parse.
     * @param expectedType
     *         The expected result type of the expression.
     * @throws ExpressionException
     *         if the expression was not syntactically correct.
     */
    private LeftValueExpression(final String expression, final Class<T> expectedType) throws ExpressionException {
        super(expression, expectedType, Bindings.bindings());
    }

    /**
     * Sets the result of an expression to a specified value. The expression is
     * treated as an <em>lvalue</em>, the expression resolves to an object whose value will be
     * set. If the expression does not resolve to an object or cannot otherwise be written to
     * (e.g. read-only), then this method will have no effect.
     *
     * @param bindings the bindings to evaluate the expression within.
     * @param value the value to set in the result of the expression evaluation.
     */
    public void set(Bindings bindings, Object value) {
        Reject.ifNull(bindings);
        try {
            valueExpression.setValue(new XLContext(bindings.asMap()), value);
        } catch (ELException ele) {
            logger.warn("An error occurred setting the result of the expression {}",
                        valueExpression.getExpressionString(),
                        ele);
            // unresolved elements are simply ignored
        }
    }
}
