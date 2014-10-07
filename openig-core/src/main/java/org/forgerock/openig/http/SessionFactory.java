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

package org.forgerock.openig.http;

import org.forgerock.http.Session;

/**
 * A SessionFactory is responsible to create a new type of {@link Session}.
 * This allows users to extends the default OpenIG behaviour quite easily.
 */
public interface SessionFactory {

    /**
     * Key to retrieve the default {@link SessionFactory} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String SESSION_FACTORY_HEAP_KEY = "Session";

    /**
     * Builds a new Session for the given Exchange. The implementations are free to keep a reference to the Exchange.
     * The session object is scoped by the Exchange's own lifecycle.
     *
     * @param exchange
     *         Exchange to create a session for.
     * @return a new Session instance.
     */
    Session build(Exchange exchange);
}
