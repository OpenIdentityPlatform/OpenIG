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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.http;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * An HTTP cookie. For more information, see the original <a href=
 * "http://web.archive.org/web/20070805052634/http://wp.netscape.com/newsref/std/cookie_spec.html"
 * > Netscape specification</a>, <a
 * href="http://www.ietf.org/rfc/rfc2109.txt">RFC 2109</a> and <a
 * href="http://www.ietf.org/rfc/rfc2965.txt">RFC 2965</a>.
 */
public class Cookie {
    /** The name of the cookie. */
    private String name;

    /** The value of the cookie. */
    private String value;

    /** The intended use of a cookie. */
    private String comment;

    /** URL identifying the intended use of a cookie. */
    private String commentURL;

    /**
     * Directs the user agent to discard the cookie unconditionally when it
     * terminates.
     */
    private Boolean discard;

    /** The domain for which the cookie is valid. */
    private String domain;

    /**
     * The lifetime of the cookie, expressed as the date and time of expiration.
     */
    private Date expires;

    /**
     * Directs the user agent to make the cookie inaccessible to client side
     * script.
     */
    private Boolean httpOnly;

    /** The lifetime of the cookie, expressed in seconds. */
    private Integer maxAge;

    /** The subset of URLs on the origin server to which this cookie applies. */
    private String path;

    /** Restricts the port(s) to which a cookie may be returned. */
    private final List<Integer> port = new ArrayList<Integer>();

    /**
     * Directs the user agent to use only secure means to send back this cookie.
     */
    private Boolean secure;

    /**
     * The version of the state management mechanism to which this cookie
     * conforms.
     */
    private Integer version;

    /**
     * Creates a new uninitialized cookie.
     */
    public Cookie() {
        // Empty cookie.
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Cookie)) {
            return false;
        }
        final Cookie other = (Cookie) obj;
        return objectsAreEqual(comment, other.comment)
                && objectsAreEqual(commentURL, other.commentURL)
                && objectsAreEqual(discard, other.discard)
                && objectsAreEqual(domain, other.domain)
                && objectsAreEqual(expires, other.expires)
                && objectsAreEqual(httpOnly, other.httpOnly)
                && objectsAreEqual(maxAge, other.maxAge)
                && objectsAreEqual(name, other.name)
                && objectsAreEqual(path, other.path)
                && objectsAreEqual(port, other.port)
                && objectsAreEqual(secure, other.secure)
                && objectsAreEqual(value, other.value)
                && objectsAreEqual(version, other.version);
    }

    /**
     * Returns the intended use of a cookie.
     *
     * @return The intended use of a cookie.
     */
    public String getComment() {
        return comment;
    }

    /**
     * Returns the URL identifying the intended use of a cookie.
     *
     * @return The URL identifying the intended use of a cookie.
     */
    public String getCommentURL() {
        return commentURL;
    }

    /**
     * Returns {@code true} if the user agent should discard the cookie
     * unconditionally when it terminates.
     *
     * @return {@code true} if the user agent should discard the cookie
     *         unconditionally when it terminates.
     */
    public Boolean getDiscard() {
        return discard;
    }

    /**
     * Returns the domain for which the cookie is valid.
     *
     * @return The domain for which the cookie is valid.
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Returns the lifetime of the cookie, expressed as the date and time of
     * expiration.
     *
     * @return The lifetime of the cookie, expressed as the date and time of
     *         expiration.
     */
    public Date getExpires() {
        return expires;
    }

    /**
     * Returns {@code true} if the user agent should make the cookie
     * inaccessible to client side script.
     *
     * @return {@code true} if the user agent should make the cookie
     *         inaccessible to client side script.
     */
    public Boolean getHttpOnly() {
        return httpOnly;
    }

    /**
     * Returns the lifetime of the cookie, expressed in seconds.
     *
     * @return The lifetime of the cookie, expressed in seconds.
     */
    public Integer getMaxAge() {
        return maxAge;
    }

    /**
     * Returns name of the cookie.
     *
     * @return The name of the cookie.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the subset of URLs on the origin server to which this cookie
     * applies.
     *
     * @return The subset of URLs on the origin server to which this cookie
     *         applies.
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the restricted list of port(s) to which a cookie may be returned.
     *
     * @return The restricted list of port(s) to which a cookie may be returned.
     */
    public List<Integer> getPort() {
        return port;
    }

    /**
     * Returns {@code true} if the user agent should use only secure means to
     * send back this cookie.
     *
     * @return {@code true} if the user agent should use only secure means to
     *         send back this cookie.
     */
    public Boolean getSecure() {
        return secure;
    }

    /**
     * Returns the value of the cookie.
     *
     * @return The value of the cookie.
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the version of the state management mechanism to which this
     * cookie conforms.
     *
     * @return The version of the state management mechanism to which this
     *         cookie conforms.
     */
    public Integer getVersion() {
        return version;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (comment == null ? 0 : comment.hashCode());
        result = prime * result + (commentURL == null ? 0 : commentURL.hashCode());
        result = prime * result + (discard == null ? 0 : discard.hashCode());
        result = prime * result + (domain == null ? 0 : domain.hashCode());
        result = prime * result + (expires == null ? 0 : expires.hashCode());
        result = prime * result + (httpOnly == null ? 0 : httpOnly.hashCode());
        result = prime * result + (maxAge == null ? 0 : maxAge.hashCode());
        result = prime * result + (name == null ? 0 : name.hashCode());
        result = prime * result + (path == null ? 0 : path.hashCode());
        result = prime * result + (port == null ? 0 : port.hashCode());
        result = prime * result + (secure == null ? 0 : secure.hashCode());
        result = prime * result + (value == null ? 0 : value.hashCode());
        result = prime * result + (version == null ? 0 : version.hashCode());
        return result;
    }

    /**
     * Sets the intended use of a cookie.
     *
     * @param comment
     *            The intended use of a cookie.
     * @return This cookie.
     */
    public Cookie setComment(final String comment) {
        this.comment = comment;
        return this;
    }

    /**
     * Sets the URL identifying the intended use of a cookie.
     *
     * @param commentURL
     *            The URL identifying the intended use of a cookie.
     * @return This cookie.
     */
    public Cookie setCommentURL(final String commentURL) {
        this.commentURL = commentURL;
        return this;
    }

    /**
     * Sets the value indicating whether the user agent should discard the
     * cookie unconditionally when it terminates.
     *
     * @param discard
     *            {@code true} if the user agent should discard the cookie
     *            unconditionally when it terminates.
     * @return This cookie.
     */
    public Cookie setDiscard(final Boolean discard) {
        this.discard = discard;
        return this;
    }

    /**
     * Sets the domain for which the cookie is valid.
     *
     * @param domain
     *            The domain for which the cookie is valid.
     * @return This cookie.
     */
    public Cookie setDomain(final String domain) {
        this.domain = domain;
        return this;
    }

    /**
     * Sets the lifetime of the cookie, expressed as the date and time of
     * expiration.
     *
     * @param expires
     *            The lifetime of the cookie, expressed as the date and time of
     *            expiration.
     * @return This cookie.
     */
    public Cookie setExpires(final Date expires) {
        this.expires = expires;
        return this;
    }

    /**
     * Sets the value indicating whether the user agent should make the cookie
     * inaccessible to client side script.
     *
     * @param httpOnly
     *            {@code true} if the user agent should make the cookie
     *            inaccessible to client side script.
     * @return this;
     */
    public Cookie setHttpOnly(final Boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    /**
     * Sets the lifetime of the cookie, expressed in seconds.
     *
     * @param maxAge
     *            The lifetime of the cookie, expressed in seconds.
     * @return This cookie.
     */
    public Cookie setMaxAge(final Integer maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    /**
     * Sets the name of the cookie.
     *
     * @param name
     *            The name of the cookie.
     * @return This cookie.
     */
    public Cookie setName(final String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the subset of URLs on the origin server to which this cookie
     * applies.
     *
     * @param path
     *            The subset of URLs on the origin server to which this cookie
     *            applies.
     * @return This cookie.
     */
    public Cookie setPath(final String path) {
        this.path = path;
        return this;
    }

    /**
     * Sets the value indicating whether the user agent should use only secure
     * means to send back this cookie.
     *
     * @param secure
     *            {@code true} if the user agent should use only secure means to
     *            send back this cookie.
     * @return This cookie.
     */
    public Cookie setSecure(final Boolean secure) {
        this.secure = secure;
        return this;
    }

    /**
     * Sets the value of the cookie.
     *
     * @param value
     *            The value of the cookie.
     * @return This cookie.
     */
    public Cookie setValue(final String value) {
        this.value = value;
        return this;
    }

    /**
     * Sets the version of the state management mechanism to which this cookie
     * conforms.
     *
     * @param version
     *            The version of the state management mechanism to which this
     *            cookie conforms.
     * @return This cookie.
     */
    public Cookie setVersion(final Integer version) {
        this.version = version;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        if (name != null) {
            builder.append("name=").append(name).append(" ");
        }
        if (value != null) {
            builder.append("value=").append(value).append(" ");
        }
        if (comment != null) {
            builder.append("comment=").append(comment).append(" ");
        }
        if (commentURL != null) {
            builder.append("commentURL=").append(commentURL).append(" ");
        }
        if (discard != null) {
            builder.append("discard=").append(discard).append(" ");
        }
        if (domain != null) {
            builder.append("domain=").append(domain).append(" ");
        }
        if (expires != null) {
            builder.append("expires=").append(expires).append(" ");
        }
        if (httpOnly != null) {
            builder.append("httpOnly=").append(httpOnly).append(" ");
        }
        if (maxAge != null) {
            builder.append("maxAge=").append(maxAge).append(" ");
        }
        if (path != null) {
            builder.append("path=").append(path).append(" ");
        }
        if (port != null) {
            builder.append("port=").append(port).append(" ");
        }
        if (secure != null) {
            builder.append("secure=").append(secure).append(" ");
        }
        if (version != null) {
            builder.append("version=").append(version);
        }
        builder.append("]");
        return builder.toString();
    }

    private static boolean objectsAreEqual(final Object o1, final Object o2) {
        if (o1 == null) {
            return o2 == null;
        } else {
            return o1.equals(o2);
        }
    }

}
