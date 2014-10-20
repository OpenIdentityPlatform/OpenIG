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

package org.forgerock.http;

public abstract class AbstractContext implements Context {

    private final String id;
    private final Context parent;

    protected AbstractContext(Context parent) {
        this(null, parent);
    }

    protected AbstractContext(String id, Context parent) {
        this.id = id;
        this.parent = parent;
    }

    public final <T extends Context> T asContext(Class<T> clazz) {
        T context = asContext0(clazz);
        if (context != null) {
            return context;
        } else {
            throw new IllegalArgumentException("No context of type " + clazz.getName() + " found.");
        }
    }

    public final Context getContext(String contextName) {
        Context context = getContext0(contextName);
        if (context != null) {
            return context;
        } else {
            throw new IllegalArgumentException("No context of type " + contextName + " found.");
        }
    }

    public final boolean containsContext(Class<? extends Context> clazz) {
        return asContext0(clazz) != null;
    }

    public final boolean containsContext(String contextName) {
        return getContext0(contextName) != null;
    }

    public final String getId() {
        return id;
    }

    public final Context getParent() {
        return parent;
    }

    public final boolean isRootContext() {
        return getParent() == null;
    }

    private <T extends Context> T asContext0(final Class<T> clazz) {
        try {
            for (Context context = this; context != null; context = context.getParent()) {
                final Class<?> contextClass = context.getClass();
                if (clazz.isAssignableFrom(contextClass)) {
                    return contextClass.asSubclass(clazz).cast(context);
                }
            }
        } catch (final Exception e) {
            throw new IllegalArgumentException(
                    "Unable to instantiate Context implementation class '" + clazz.getName() + "'", e);
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
}
