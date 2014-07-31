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
 * Copyright 2009 Sun Microsystems Inc.
 * Portions Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.http;

import java.net.URI;

/**
 * A request message.
 */
public final class Request extends Message<Request> {

    /** Exposes incoming request cookies. */
    private final RequestCookies cookies = new RequestCookies(this);

    /**
     * Exposes query parameters and {@code application/x-www-form-urlencoded}
     * entity as values.
     */
    private final FormAttributes form = new FormAttributes(this);

    /** The method to be performed on the resource. */
    private String method;

    /** The fully-qualified URI of the resource being accessed. */
    private URI uri;

    /**
     * Creates a new request message.
     */
    public Request() {
        // Nothing to do.
    }

    /**
     * Returns the incoming request cookies.
     *
     * @return The incoming request cookies.
     */
    public RequestCookies getCookies() {
        return cookies;
    }

    /**
     * Returns the query parameters and
     * {@code application/x-www-form-urlencoded} entity as values.
     *
     * @return The query parameters and
     *         {@code application/x-www-form-urlencoded} entity as values.
     */
    public FormAttributes getForm() {
        return form;
    }

    /**
     * Returns the method to be performed on the resource.
     *
     * @return The method to be performed on the resource.
     */
    public String getMethod() {
        return method;
    }

    /**
     * Returns the fully-qualified URI of the resource being accessed.
     *
     * @return The fully-qualified URI of the resource being accessed.
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Sets the method to be performed on the resource.
     *
     * @param method
     *            The method to be performed on the resource.
     * @return This request.
     */
    public Request setMethod(final String method) {
        this.method = method;
        return this;
    }

    /**
     * Sets the fully-qualified URI of the resource being accessed.
     *
     * @param uri
     *            The fully-qualified URI of the resource being accessed.
     * @return This request.
     */
    public Request setUri(final URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    Request thisMessage() {
        return this;
    }
}
