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

package org.forgerock.http;

import java.io.IOException;

/**
 * <p>A SessionFactory is responsible to create a new type of {@link Session}.</p>
 *
 * <p>This allows users to extends the default OpenIG behaviour quite easily.</p>
 *
 * @since 1.0.0
 */
public interface SessionManager {

    /**
     * <p>Loads a new Session for the given {@link Request}. The implementations are free to keep a reference to the
     * {@code Request}.</p>
     *
     * <p>The session object is scoped by the {@code Request}'s own lifecycle.</p>
     *
     * @param request Request to create a session for.
     * @return a new Session instance.
     */
    Session load(Request request);

    void save(Session session, Response response) throws IOException;
}
