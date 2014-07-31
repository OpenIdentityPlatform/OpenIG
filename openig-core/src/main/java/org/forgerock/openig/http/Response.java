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

/**
 * A response message.
 */
public final class Response extends Message<Response> {
    /** The response status reason. */
    private String reason;

    /** The response status code. */
    private Integer status;

    /**
     * Creates a new response.
     */
    public Response() {
        // Nothing to do.
    }

    /**
     * Returns the response status reason.
     *
     * @return The response status reason.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns the response status code.
     *
     * @return The response status code.
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the response status reason.
     *
     * @param reason
     *            The response status reason.
     * @return This response.
     */
    public Response setReason(final String reason) {
        this.reason = reason;
        return this;
    }

    /**
     * Sets the response status code.
     *
     * @param status
     *            The response status code.
     * @return This response.
     */
    public Response setStatus(final Integer status) {
        this.status = status;
        return this;
    }

    @Override
    Response thisMessage() {
        return this;
    }
}
