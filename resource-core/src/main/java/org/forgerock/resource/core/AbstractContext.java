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
 * Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.resource.core;

import org.forgerock.util.Reject;

import java.util.Iterator;

/**
 * A base implementation of the context associated with a request currently
 * being processed by a {@code Handler}. A request context can be used to
 * query state information about the request. Implementations may provide
 * additional information, time-stamp information, HTTP headers, etc. Contexts
 * are linked together to form a parent-child chain of context, whose root is a
 * {@link RootContext}.
 *
 * @since 1.0.0
 */
public abstract class AbstractContext implements Context {

    private final String id;
    private final String name;
    private final Context parent;

    /**
     * Constructs a new {@code AbstractContext} with a {@code null} {@code id}.
     *
     * @param parent The parent context.
     * @param name The name of the context.
     */
    protected AbstractContext(Context parent, String name) {
        this(null, name, parent);
    }

    /**
     * Constructs a new {@code AbstractContext}.
     *
     * @param id The id of the context.
     * @param parent The parent context.
     * @param name The name of the context.
     */
    protected AbstractContext(String id, String name, Context parent) {
        this.id = id;
        this.name = name;
        this.parent = parent;
    }

    @Override
    public final String getContextName() {
        return name;
    }

    @Override
    public final <T extends Context> T asContext(Class<T> clazz) {
        Reject.ifNull(clazz, "clazz cannot be null");
        T context = asContext0(clazz);
        if (context != null) {
            return context;
        } else {
            throw new IllegalArgumentException("No context of type " + clazz.getName() + " found.");
        }
    }

    @Override
    public final Context getContext(String contextName) {
        Context context = getContext0(contextName);
        if (context != null) {
            return context;
        } else {
            throw new IllegalArgumentException("No context of named " + contextName + " found.");
        }
    }

    @Override
    public final boolean containsContext(Class<? extends Context> clazz) {
        return asContext0(clazz) != null;
    }

    @Override
    public final boolean containsContext(String contextName) {
        return getContext0(contextName) != null;
    }

    @Override
    public final String getId() {
        if (id == null && !isRootContext()) {
            return getParent().getId();
        } else {
            return id;
        }
    }

    @Override
    public final Context getParent() {
        return parent;
    }

    @Override
    public final boolean isRootContext() {
        return getParent() == null;
    }

    private <T extends Context> T asContext0(final Class<T> clazz) {
        for (Context context = this; context != null; context = context.getParent()) {
            final Class<?> contextClass = context.getClass();
            if (clazz.isAssignableFrom(contextClass)) {
                return contextClass.asSubclass(clazz).cast(context);
            }
        }
        return null;
    }

    private Context getContext0(final String contextName) {
        for (Context context = this; context != null; context = context.getParent()) {
            if (context.getContextName().equals(contextName)) {
                return context;
            }
        }
        return null;
    }

    @Override
    public Iterator<Context> iterator() { //TODO to implement
        return null;
//        return new Iterator<Context>() {
//            @Override
//            public boolean hasNext() {
//                return false;
//            }
//
//            @Override
//            public Context next() {
//                return null;
//            }
//        };
    }

    @Override
    public <T extends Context> Iterator<T> iterator(Class<T> clazz) { //TODO to implement
        return null;
//        return new Iterator<T>() {
//            @Override
//            public boolean hasNext() {
//                return false;
//            }
//
//            @Override
//            public T next() {
//                return null;
//            }
//        };
    }
}
