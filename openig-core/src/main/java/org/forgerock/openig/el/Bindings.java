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
 * Copyright 2015 ForgeRock AS.
 *
 */

package org.forgerock.openig.el;

import static java.util.Collections.unmodifiableMap;

import java.util.LinkedHashMap;
import java.util.Map;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.http.Exchange;
import org.forgerock.services.context.Context;
import org.forgerock.util.Reject;

/**
 * Bindings represents the Expression bindings used during evaluation and assignation.
 */
public class Bindings {

    private final Map<String, Object> map = new LinkedHashMap<>();

    /**
     * Returns an empty {@link Bindings} instance (mutable).
     *
     * @return an empty {@link Bindings} instance.
     */
    public static Bindings bindings() {
        return new Bindings();
    }

    /**
     * Returns a {@link Bindings} initialized with the given {@code exchange} and {@code request}.
     *
     * <p>This method is intended for compatibility until chained {@link Context} are made available in expressions.
     *
     * @param exchange
     *         The exchange to expose
     * @param request
     *         The request to expose
     * @return an initialized {@link Bindings} instance.
     */
    public static Bindings bindings(Exchange exchange, Request request) {
        return bindings((Context) exchange, request)
                .bind("exchange", exchange);
    }

    /**
     * Returns a {@link Bindings} initialized with the given {@code exchange}, {@code request} and {@code response}.
     *
     * <p>This method is intended for compatibility until chained {@link Context} are made available in expressions.
     *
     * @param exchange
     *         The exchange to expose
     * @param request
     *         The request to expose
     * @param response
     *         The response to expose
     * @return an initialized {@link Bindings} instance.
     */
    public static Bindings bindings(Exchange exchange, Request request, Response response) {
        return bindings((Context) exchange, request, response)
                .bind("exchange", exchange);
    }

    /**
     * Returns a {@link Bindings} initialized with the given {@code context} and {@code request}.
     *
     * @param context
     *         The context to expose
     * @param request
     *         The request to expose
     * @return an initialized {@link Bindings} instance.
     */
    public static Bindings bindings(Context context, Request request) {
        return bindings()
                .bind("context", context)
                .bind("request", request);
    }

    /**
     * Returns a {@link Bindings} initialized with the given {@code context}, {@code request} and {@code response}.
     *
     * @param context
     *         The context to expose
     * @param request
     *         The request to expose
     * @param response
     *         The response to expose
     * @return an initialized {@link Bindings} instance.
     */
    public static Bindings bindings(Context context, Request request, Response response) {
        return bindings(context, request)
                .bind("response", response);
    }

    /**
     * Returns a singleton {@link Bindings} initialized with the given {@code name} and {@code value}.
     *
     * @param name
     *         binding name
     * @param value
     *         binding value
     * @return an initialized {@link Bindings} instance.
     */
    public static Bindings bindings(final String name, final Object value) {
        return bindings().bind(name, value);
    }

    /**
     * Binds a new {@code value} to the given {@code name}.
     *
     * @param name
     *         binding name (must not be {@code null})
     * @param value
     *         binding value
     * @return this {@link Bindings}
     */
    public Bindings bind(String name, Object value) {
        Reject.ifNull(name);
        map.put(name, value);
        return this;
    }

    /**
     * Returns an unmodifiable {@code Map} view of this {@code Bindings} instance.
     * <p>
     * Note that while the consumer of the Map cannot modify it, any changes done through the Bindings methods
     * will be reflected in the returned Map.
     *
     * @return an unmodifiable {@code Map} view of this instance (never {@code null}).
     */
    public Map<String, Object> asMap() {
        return unmodifiableMap(map);
    }
}
