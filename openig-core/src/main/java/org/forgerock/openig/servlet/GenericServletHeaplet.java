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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.heap.HeapException;

/**
 * Generic heaplet base class for Java servlets and filters. Implements the
 * {@link ServletConfig} interface for initialization of the servlet.
 */
public abstract class GenericServletHeaplet extends CommonHeaplet implements ServletConfig {

    /** The servlet being managed by this heaplet. */
    private HttpServlet servlet;

    /**
     * Initializes the heaplet and creates the servlet. The servlet is created through a call to the abstract
     * {@link #createServlet()} method. Once created, the servlet is initialized through a call to its
     * {@link HttpServlet#init(ServletConfig) init(ServletConfig)} method.
     *
     * @throws HeapException
     *             If an exception occurs during initialization.
     * @return The generic heaplet.
     */
    @Override // GenericHeaplet
    public final Object create() throws HeapException {
        configure();
        servlet = createServlet();
        try {
            servlet.init(this);
        } catch (ServletException se) {
            throw new HeapException(se);
        }
        return servlet;
    }

    /**
     * Calls the servlet's {@code destroy()} method to notify it that it is being taken
     * out of service. Once destroyed, the servlet is dereferenced by the heaplet.
     */
    @Override // GenericHeaplet
    public void destroy() {
        servlet.destroy();
        servlet = null;
        super.destroy();
    }

    /**
     * Returns the name of the servlet.
     *
     * @return The servlet name.
     */
    @Override // ServletConfig
    public String getServletName() {
        return super.name;
    }

    /**
     * Called to request the heaplet create a servlet object. Called by {@link #create()}.
     *
     * @throws HeapException
     *             if an exception occurred during creation of the heap object or any of its dependencies.
     * @throws JsonValueException
     *             if the heaplet (or one of its dependencies) has a malformed configuration.
     * @return The created and initialized servlet.
     */
    public abstract HttpServlet createServlet() throws HeapException;
}
