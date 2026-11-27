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
 * Copyright 2026 3A Systems, LLC.
 */

package org.forgerock.openig.el;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.el.ELContext;

import de.odysseus.el.ExpressionFactoryImpl;
import de.odysseus.el.tree.Bindings;
import de.odysseus.el.tree.TreeBuilder;
import de.odysseus.el.tree.impl.Builder;
import de.odysseus.el.tree.impl.Builder.Feature;
import de.odysseus.el.tree.impl.Parser;
import de.odysseus.el.tree.impl.ast.AstBinary;
import de.odysseus.el.tree.impl.ast.AstBracket;
import de.odysseus.el.tree.impl.ast.AstChoice;
import de.odysseus.el.tree.impl.ast.AstDot;
import de.odysseus.el.tree.impl.ast.AstFunction;
import de.odysseus.el.tree.impl.ast.AstMethod;
import de.odysseus.el.tree.impl.ast.AstNode;
import de.odysseus.el.tree.impl.ast.AstParameters;
import de.odysseus.el.tree.impl.ast.AstProperty;
import de.odysseus.el.tree.impl.ast.AstRightValue;
import de.odysseus.el.tree.impl.ast.AstUnary;

import org.forgerock.openig.el.Expression.ReadUse;
import org.forgerock.openig.el.Expression.XLContext;

/** Adds read context to a policy evaluation while leaving value resolution and EL operations to JUEL. */
final class ReadTrackingExpressionFactory extends ExpressionFactoryImpl {
    @Override
    protected TreeBuilder createTreeBuilder(Properties properties, Feature... features) {
        return new Builder(features) {
            @Override
            protected Parser createParser(String expression) {
                return new ReadTrackingParser(this, expression);
            }
        };
    }

    private static final class ReadTrackingParser extends Parser {
        private ReadTrackingParser(Builder builder, String expression) {
            super(builder, expression);
        }

        @Override
        protected AstChoice createAstChoice(AstNode question, AstNode yes, AstNode no) {
            return super.createAstChoice(new ReadScope(question, Scope.OPERAND), yes, no);
        }

        @Override
        protected AstUnary createAstUnary(AstNode child, AstUnary.Operator operator) {
            return super.createAstUnary(new ReadScope(child, Scope.OPERAND), operator);
        }

        @Override
        protected AstBinary createAstBinary(AstNode left, AstNode right, AstBinary.Operator operator) {
            return super.createAstBinary(new ReadScope(left, Scope.OPERAND),
                    new ReadScope(right, Scope.OPERAND), operator);
        }

        @Override
        protected AstDot createAstDot(AstNode base, String property, boolean lvalue) {
            return super.createAstDot(new ReadScope(base, Scope.RECEIVER), property, lvalue);
        }

        @Override
        protected AstBracket createAstBracket(AstNode base, AstNode property, boolean lvalue, boolean strict) {
            return super.createAstBracket(new ReadScope(base, Scope.RECEIVER),
                    new ReadScope(property, Scope.OPERAND), lvalue, strict);
        }

        @Override
        protected AstFunction createAstFunction(String name, int index, AstParameters parameters) {
            return new AstFunction(name, index, observeArguments(parameters), context.isEnabled(Feature.VARARGS)) {
                @Override
                public Object eval(Bindings bindings, ELContext context) {
                    return ((XLContext) context).evaluateFunction(bindings.getFunction(index),
                            () -> super.eval(bindings, context));
                }
            };
        }

        @Override
        protected AstMethod createAstMethod(AstProperty property, AstParameters parameters) {
            return new AstMethod(property, observeArguments(parameters)) {
                @Override
                public Object eval(Bindings bindings, ELContext context) {
                    return ((XLContext) context).evaluateCall(() -> super.eval(bindings, context));
                }
            };
        }

        private static AstParameters observeArguments(AstParameters parameters) {
            List<AstNode> arguments = new ArrayList<>(parameters.getCardinality());
            for (int i = 0; i < parameters.getCardinality(); i++) {
                arguments.add(new ReadScope(parameters.getChild(i), Scope.ARGUMENT));
            }
            return new AstParameters(arguments);
        }
    }

    private enum Scope {
        OPERAND, RECEIVER, ARGUMENT
    }

    private static final class ReadScope extends AstRightValue {
        private final AstNode child;
        private final Scope scope;

        private ReadScope(AstNode child, Scope scope) {
            this.child = child;
            this.scope = scope;
        }

        @Override
        public Object eval(Bindings bindings, ELContext context) {
            XLContext evaluation = (XLContext) context;
            if (scope == Scope.ARGUMENT) {
                return evaluation.evaluateArgument(() -> child.eval(bindings, context));
            }
            ReadUse previous = evaluation.readUse;
            evaluation.readUse = scope == Scope.RECEIVER ? previous.asReceiver() : ReadUse.OPERAND;
            try {
                return child.eval(bindings, context);
            } finally {
                evaluation.readUse = previous;
            }
        }

        @Override
        public void appendStructure(StringBuilder builder, Bindings bindings) {
            child.appendStructure(builder, bindings);
        }

        @Override
        public int getCardinality() {
            return 1;
        }

        @Override
        public AstNode getChild(int index) {
            return index == 0 ? child : null;
        }
    }
}
