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
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.servlet;

// Java Standard Edition
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

// JSON Fluent
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.heap.NestedHeaplet;

/**
 * Heaplet with methods common to servlets and filters.
 *
 * @author Paul C. Bryan
 */
abstract class CommonHeaplet extends NestedHeaplet {

    /** Initialization parameters to supply to servlet or filter. */
    protected final Map<String, String> initParams = new HashMap<String, String>();

    /** Context to supply in configuration during initialization. */
    protected ServletContext servletContext;

    /**
     * Returns the servlet context in which the caller is executing.
     */
    public ServletContext getServletContext() { // FilterConfig, ServletConfig
        return servletContext;
    }

    /**
     * Returns the value of the named initialization parameter.
     *
     * @param name the name of the initialization parameter to get.
     * @return the value of the the initialization parameter, or {@code null} if the parameter does not exist.
     */
    public String getInitParameter(String name) { // FilterConfig, ServletConfig
        return initParams.get(name);
    }

    /**
     * Returns the names of the initialization parameters, or an empty enumeration if there
     * are no initialization parameters.
     */
    public Enumeration<String> getInitParameterNames() { // FilterConfig, ServletConfig
        return Collections.enumeration(initParams.keySet());
    }

    /**
     * Configures the servlet context and initialization parameters.
     *
     * @throws HeapException if an exception occurred during creation of the heap object or any of its dependencies.
     * @throws JsonValueException if the heaplet (or one of its dependencies) has a malformed configuration.
     */
    protected void configure() throws HeapException, JsonValueException {
        servletContext = HeapUtil.getRequiredObject(heap, config.get("servletContext").defaultTo("ServletContext"), ServletContext.class);
        JsonValue initParams = config.get("initParams").expect(Map.class); // optional
        for (String key : initParams.keys()) {
            this.initParams.put(key, initParams.get(key).asString());
        }
    }
}
