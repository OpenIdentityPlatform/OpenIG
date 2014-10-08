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

package org.forgerock.http.servlet;

import org.forgerock.http.Handler;
import org.forgerock.http.SessionFactory;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.io.IO;
import org.forgerock.util.Factory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

import static org.forgerock.http.servlet.HttpFrameworkServlet.LOG;

/**
 * <p>Configuration class to configure the {@link HttpFrameworkServlet}.</p>
 *
 * <p>The implementation of this class will be loaded using the {@link java.util.ServiceLoader} framework.</p>
 *
 * @since 1.0.0
 */
public abstract class ServletConfiguration {

    /**
     * Initialises the {@code ServletConfiguration} configuration properties.
     *
     * @param config The {@link ServletConfig}.
     * @throws ServletException If there is an error initialising the configuration.
     */
    public void init(ServletConfig config) throws ServletException {
        // Nothing to do by default
    }

    /**
     * Gets the root {@link Handler} that will handle all HTTP requests.
     *
     * @return The {@code Handler} to handle HTTP requests.
     * @throws ServletException If there is an error creating the root {@code Handler}.
     */
    public abstract Handler getRootHandler() throws ServletException;

    /**
     * Gets the {@link Factory} that will create temporary storage {@link Buffer}s to handle the processing of requests.
     *
     * @return A {@code Buffer} {@code Factory}.
     * @throws ServletException If there is an error creating the buffer factory.
     */
    public Factory<Buffer> getBufferFactory() throws ServletException {
        try {
            return IO.newTemporaryStorage(File.createTempFile("tmpStorage", null), IO.DEFAULT_TMP_INIT_LENGTH,
                    IO.DEFAULT_TMP_MEMORY_LIMIT, IO.DEFAULT_TMP_FILE_LIMIT);
        } catch (IOException e) {
            LOG.debug("Failed to create temporary storage", e);
            throw new ServletException("Failed to create temporary storage", e);
        }
    }

    /**
     * <p>Gets the {@link SessionFactory} instance that will be used to create {@link org.forgerock.http.Session}
     * instances for HTTP requests.</p>
     *
     * <p>By default {@code null} will be returned which will default to use the
     * {@link org.forgerock.http.servlet.ServletSession}.</p>
     *
     * @return A {@code SessionFactory} instance or {@code null}.
     * @throws ServletException If there is an error creating the {@code SessionFactory}.
     */
    public SessionFactory getSessionFactory() throws ServletException {
        return null;
    }
}
