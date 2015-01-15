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
 * Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.resource.core;

import java.util.UUID;

/**
 * A {@link Context} which has an an ID but no parent. All request context
 * chains are terminated by a root context as the top-most context.
 *
 * @since 1.0.0
 */
public final class RootContext extends AbstractContext {

    private static final ThreadLocal<String> ID_CACHE = new ThreadLocal<String>() {

        private final String baseId = UUID.randomUUID().toString();
        private long count = 0;

        @Override
        protected String initialValue() {
            return baseId + count;
        }
    };

    public RootContext() {
        this(ID_CACHE.get());
    }

    public RootContext(String id) {
        super(id, "root", null); // No parent
    }
}
