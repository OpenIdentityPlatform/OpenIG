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
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.http;

/**
 * A response message.
 */
public final class Response extends MessageImpl<Response> {
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

    @Override
    public Response setEntity(Object o) {
        setEntity0(o);
        return this;
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

    /**
     * Sets the response status code and reason as per RFC 2616 §6.1.1. If the
     * status code is unrecognized, then "{@code Uncertain}" is used.
     *
     * @param status
     *            The response status code.
     * @return This response.
     */
    public Response setStatusAndReason(final Integer status) {
        this.status = status;
        this.reason = status != null ? getReason(status) : null;
        return this;
    }

    @Override
    public Response setVersion(String version) {
        setVersion0(version);
        return this;
    };

    private static String getReason(int status) {
        switch (status) {
        case 100:
            return "Continue";
        case 101:
            return "Switching Protocols";
        case 200:
            return "OK";
        case 201:
            return "Created";
        case 202:
            return "Accepted";
        case 203:
            return "Non-Authoritative Information";
        case 204:
            return "No Content";
        case 205:
            return "Reset Content";
        case 206:
            return "Partial Content";
        case 300:
            return "Multiple Choices";
        case 301:
            return "Moved Permanently";
        case 302:
            return "Found";
        case 303:
            return "See Other";
        case 304:
            return "Not Modified";
        case 305:
            return "Use Proxy";
        case 307:
            return "Temporary Redirect";
        case 400:
            return "Bad Request";
        case 401:
            return "Unauthorized";
        case 402:
            return "Payment Required";
        case 403:
            return "Forbidden";
        case 404:
            return "Not Found";
        case 405:
            return "Method Not Allowed";
        case 406:
            return "Not Acceptable";
        case 407:
            return "Proxy Authentication Required";
        case 408:
            return "Request Time-out";
        case 409:
            return "Conflict";
        case 410:
            return "Gone";
        case 411:
            return "Length Required";
        case 412:
            return "Precondition Failed";
        case 413:
            return "Request Entity Too Large";
        case 414:
            return "Request-URI Too Large";
        case 415:
            return "Unsupported Media Type";
        case 416:
            return "Requested range not satisfiable";
        case 417:
            return "Expectation Failed";
        case 500:
            return "Internal Server Error";
        case 501:
            return "Not Implemented";
        case 502:
            return "Bad Gateway";
        case 503:
            return "Service Unavailable";
        case 504:
            return "Gateway Time-out";
        case 505:
            return "HTTP Version not supported";
        }
        return "Uncertain"; // not specified per RFC 2616
    }

    /**
     * Returns {@code true} if this response represents an HTTP error.
     *
     * @return {@code true} if this response represents an HTTP error.
     */
    public boolean isError() {
        return getStatus() < 200 || getStatus() >= 300;
    }
}
