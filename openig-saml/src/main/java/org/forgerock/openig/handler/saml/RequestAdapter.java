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

package org.forgerock.openig.handler.saml;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.forgerock.openig.http.Exchange;

/**
 * Adapts a given {@link HttpServletRequest} to make uses of the {@link Exchange} in some places.
 */
class RequestAdapter extends HttpServletRequestWrapper {

    /**
     * Provides additional data (parameter values).
     */
    private final Exchange exchange;

    RequestAdapter(final HttpServletRequest request, final Exchange exchange) {
        super(request);
        this.exchange = exchange;
    }

    @Override
    public String getParameter(final String name) {
        final List<String> values = exchange.request.getForm().get(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> parameters = new HashMap<String, String[]>();
        for (Map.Entry<String, List<String>> entry : exchange.request.getForm().entrySet()) {
            List<String> values = entry.getValue();
            parameters.put(entry.getKey(), values.toArray(new String[values.size()]));
        }
        return Collections.unmodifiableMap(parameters);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(exchange.request.getForm().keySet());
    }

    @Override
    public String[] getParameterValues(final String name) {
        final List<String> values = exchange.request.getForm().get(name);
        if (values == null) {
            return null;
        }
        return values.toArray(new String[values.size()]);
    }
}
