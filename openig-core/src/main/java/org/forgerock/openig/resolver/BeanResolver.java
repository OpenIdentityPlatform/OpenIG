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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.resolver;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.VariableMapper;

/**
 * Resolves Java Beans objects.
 */
public class BeanResolver implements Resolver {

    private final BeanELResolver delegate;
    private final ELContext context;

    public BeanResolver() {
        delegate = new BeanELResolver();
        context = new BasicELContext();
    }

    @Override
    public Class<?> getKey() {
        return Object.class;
    }

    @Override
    public Object get(final Object object, final Object element) {
        try {
            final Object value = delegate.getValue(context, object, element);
            if (context.isPropertyResolved()) {
                return value;
            }
        } catch (Exception e) {
            // Ignored, considered as un-resolved
        }
        return UNRESOLVED;
    }

    @Override
    public Object put(final Object object, final Object element, final Object value) {
        try {
            delegate.setValue(context, object, element, value);
        } catch (Exception e) {
            // Ignored, let other resolvers take over
        }
        return UNRESOLVED;
    }

    private class BasicELContext extends ELContext {
        @Override
        public ELResolver getELResolver() {
            return delegate;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }
    }
}
