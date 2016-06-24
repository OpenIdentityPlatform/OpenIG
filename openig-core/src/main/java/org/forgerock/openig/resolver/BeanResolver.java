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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.resolver;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.VariableMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves Java Beans objects.
 *
 * <p>Notice that this object is considered as a fallback in the resolution mechanism.
 * It MUST NOT be declared in the {@literal META-INF/services/org.forgerock.openig.resolver.Resolver} services file.
 */
public class BeanResolver implements Resolver {

    private static final Logger logger = LoggerFactory.getLogger(BeanResolver.class);

    private final BeanELResolver delegate;
    private final ELContext context;

    /** The unique resolver instance. */
    static final BeanResolver INSTANCE = new BeanResolver();

    /**
     * Builds a new BeanResolver around an EL {@link BeanELResolver} instance.
     */
    public BeanResolver() {
        delegate = new BeanELResolver();
        context = new BasicELContext();
    }

    /**
     * Do not forget to override this method in sub-classes.
     * @return the {@code Object.class} type reference.
     */
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
            logger.warn("An error occurred during the resolution", e);
            // Ignored, considered as un-resolved
        }
        return UNRESOLVED;
    }

    @Override
    public Object put(final Object object, final Object element, final Object value) {
        try {
            delegate.setValue(context, object, element, value);
        } catch (Exception e) {
            logger.warn("An error occurred during the resolution", e);
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
