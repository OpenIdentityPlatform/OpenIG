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

import static org.forgerock.http.URIUtil.*;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A MutableUri is a modifiable {@link URI} substitute.
 * Unlike URIs, which are immutable, a MutableUri can have its fields updated independently.
 * That makes it easier if you just want to change a element of an Uri.
 *
 * @see URI
 */
public final class MutableUri implements Comparable<MutableUri> {

    /**
     * Factory method for avoiding typing {@code new MutableUri("http://...")}.
     * @param uri URL encoded URI
     * @return a new MutableUri instance
     * @throws URISyntaxException if the given Uri is not well-formed
     */
    public static MutableUri uri(String uri) throws URISyntaxException {
        return new MutableUri(uri);
    }

    /**
     * The real URI, hidden by this class. Recreated each time a field is updated.
     */
    private URI uri;

    /**
     * Builds a new MutableUri using the given URI.
     * @param uri URI
     */
    public MutableUri(final URI uri) {
        this.uri = uri;
    }

    /**
     * Builds a new MutableUri with deep copy.
     * @param mutableUri URI
     */
    public MutableUri(final MutableUri mutableUri) {
        this.uri = mutableUri.asURI();
    }

    /**
     * Builds a new MutableUri using the given URL encoded String URI.
     * @param uri URL encoded URI
     * @throws URISyntaxException if the given Uri is not well-formed
     */
    public MutableUri(final String uri) throws URISyntaxException {
        this.uri = new URI(uri);
    }

    /**
     * Builds a new MutableUri using the given fields values (decoded values).
     * @param scheme Scheme name
     * @param userInfo User name and authorization information
     * @param host Host name
     * @param port Port number
     * @param path Path
     * @param query Query
     * @param fragment Fragment
     * @throws URISyntaxException if the produced URI is not well-formed
     */
    public MutableUri(String scheme,
                      String userInfo,
                      String host,
                      int port,
                      String path,
                      String query,
                      String fragment)
            throws URISyntaxException {
        uri = new URI(scheme, userInfo, host, port, path, query, fragment);
    }

    /**
     * Returns the equivalent {@link URI} instance.
     * @return the equivalent {@link URI} instance.
     */
    public URI asURI() {
        return uri;
    }

    /**
     * Returns the scheme name.
     * @return the scheme name.
     */
    public String getScheme() {
        return uri.getScheme();
    }

    /**
     * Update the scheme of this MutableUri.
     * @param scheme new scheme name
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setScheme(final String scheme) throws URISyntaxException {
        this.uri = new URI(scheme,
                           uri.getUserInfo(),
                           uri.getHost(),
                           uri.getPort(),
                           uri.getPath(),
                           uri.getQuery(),
                           uri.getFragment());
    }

    /**
     * Returns the user info element.
     * @return the user info element.
     */
    public String getUserInfo() {
        return uri.getUserInfo();
    }

    /**
     * Returns the raw (encoded) user info element.
     * @return the raw user info element.
     */
    public String getRawUserInfo() {
        return uri.getRawUserInfo();
    }

    /**
     * Update the user info (not encoded) of this MutableUri.
     * @param userInfo new user info element (not encoded)
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setUserInfo(final String userInfo) throws URISyntaxException {
        this.uri = new URI(uri.getScheme(),
                           userInfo,
                           uri.getHost(),
                           uri.getPort(),
                           uri.getPath(),
                           uri.getQuery(),
                           uri.getFragment());
    }

    /**
     * Update the user info (encoded) of this MutableUri.
     * @param rawUserInfo new user info element (encoded)
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setRawUserInfo(String rawUserInfo) throws URISyntaxException {
        uri = create(uri.getScheme(),
                     rawUserInfo,
                     uri.getHost(),
                     uri.getPort(),
                     uri.getRawPath(),
                     uri.getRawQuery(),
                     uri.getRawFragment());
    }

    /**
     * Returns the host element.
     * @return the host element.
     */
    public String getHost() {
        return uri.getHost();
    }

    /**
     * Update the host name of this MutableUri.
     * @param host new host element
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setHost(final String host) throws URISyntaxException {
        this.uri = new URI(uri.getScheme(),
                           uri.getUserInfo(),
                           host,
                           uri.getPort(),
                           uri.getPath(),
                           uri.getQuery(),
                           uri.getFragment());
    }

    /**
     * Returns the port element.
     * @return the port element.
     */
    public int getPort() {
        return uri.getPort();
    }

    /**
     * Update the port of this MutableUri.
     * @param port new port number
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setPort(final int port) throws URISyntaxException {
        uri = new URI(uri.getScheme(),
                      uri.getUserInfo(),
                      uri.getHost(),
                      port,
                      uri.getPath(),
                      uri.getQuery(),
                      uri.getFragment());
    }

    /**
     * Returns the path element.
     * @return the path element.
     */
    public String getPath() {
        return uri.getPath();
    }

    /**
     * Returns the raw (encoded) path element.
     * @return the raw path element.
     */
    public String getRawPath() {
        return uri.getRawPath();
    }

    /**
     * Update the path (not encoded) of this MutableUri.
     * @param path new path element (not encoded)
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setPath(final String path) throws URISyntaxException {
        this.uri = new URI(uri.getScheme(),
                           uri.getUserInfo(),
                           uri.getHost(),
                           uri.getPort(),
                           path,
                           uri.getQuery(),
                           uri.getFragment());

    }

    /**
     * Update the pah (encoded) of this MutableUri.
     * @param rawPath new path element (encoded)
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setRawPath(String rawPath) throws URISyntaxException {
        uri = create(uri.getScheme(),
                     uri.getRawUserInfo(),
                     uri.getHost(),
                     uri.getPort(),
                     rawPath,
                     uri.getRawQuery(),
                     uri.getRawFragment());
    }

    /**
     * Returns the path element.
     * @return the path element.
     */
    public String getQuery() {
        return uri.getQuery();
    }

    /**
     * Returns the raw (encoded) query element.
     * @return the raw query element.
     */
    public String getRawQuery() {
        return uri.getRawQuery();
    }

    /**
     * Update the query string (not encoded) of this MutableUri.
     * @param query new query string element (not encoded)
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setQuery(final String query) throws URISyntaxException {
        this.uri = new URI(uri.getScheme(),
                           uri.getUserInfo(),
                           uri.getHost(),
                           uri.getPort(),
                           uri.getPath(),
                           query,
                           uri.getFragment());
    }

    /**
     * Update the query (encoded) of this MutableUri.
     * @param rawQuery new query element (encoded)
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setRawQuery(String rawQuery) throws URISyntaxException {
        uri = create(uri.getScheme(),
                     uri.getRawUserInfo(),
                     uri.getHost(),
                     uri.getPort(),
                     uri.getRawPath(),
                     rawQuery,
                     uri.getRawFragment());
    }

    /**
     * Returns the fragment element.
     * @return the fragment element.
     */
    public String getFragment() {
        return uri.getFragment();
    }

    /**
     * Returns the raw (encoded) fragment element.
     * @return the raw fragment element.
     */
    public String getRawFragment() {
        return uri.getRawFragment();
    }

    /**
     * Update the fragment (not encoded) of this MutableUri.
     * @param fragment new fragment element (not encoded)
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setFragment(final String fragment) throws URISyntaxException {
        this.uri = new URI(uri.getScheme(),
                           uri.getUserInfo(),
                           uri.getHost(),
                           uri.getPort(),
                           uri.getPath(),
                           uri.getQuery(),
                           fragment);
    }

    /**
     * Update the fragment (encoded) of this MutableUri.
     * @param rawFragment new framgent element (encoded)
     * @throws URISyntaxException if the new equivalent URI is invalid
     */
    public void setRawFragment(String rawFragment) throws URISyntaxException {
        uri = create(uri.getScheme(),
                     uri.getRawUserInfo(),
                     uri.getHost(),
                     uri.getPort(),
                     uri.getRawPath(),
                     uri.getRawQuery(),
                     rawFragment);
    }

    /**
     * Returns the authority compound element.
     * @return the authority compound element.
     */
    public String getAuthority() {
        return uri.getAuthority();
    }

    /**
     * Returns the raw (encoded) authority compound element.
     * @return the authority compound element.
     */
    public String getRawAuthority() {
        return uri.getRawAuthority();
    }

    /**
     * Changes the base scheme, host and port of this MutableUri to that specified in a base URI,
     * or leaves them unchanged if the base URI is {@code null}. This implementation only
     * uses scheme, host and port. The remaining components of the URI remain intact.
     *
     * @param base the URI to base the other URI on.
     * @return this (rebased) instance
     */
    public MutableUri rebase(MutableUri base) {
        if (base == null) {
            return this;
        }
        String scheme = base.getScheme();
        String host = base.getHost();
        int port = base.getPort();
        if (scheme == null || host == null) {
            return this;
        }
        try {
            setScheme(scheme);
            setHost(host);
            setPort(port);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        return this;
    }

    /**
     * Changes the base scheme, host and port of this MutableUri to that specified in a base URI,
     * or leaves them unchanged if the base URI is {@code null}. This implementation only
     * uses scheme, host and port. The remaining components of the URI remain intact.
     *
     * @param base the URI to base the other URI on.
     * @return this (rebased) instance
     */
    public MutableUri rebase(URI base) {
        return rebase(new MutableUri(base));
    }

    @Override
    public int compareTo(final MutableUri o) {
        return asURI().compareTo(o.asURI());
    }

    /**
     * Relativizes the given URI against this URI.
     * @param uri the uri to relativizes against this instance
     * @return this instance (mutated)
     * @see URI#relativize(URI)
     */
    public MutableUri relativize(final MutableUri uri) {
        this.uri = this.uri.relativize(uri.asURI());
        return this;
    }

    /**
     * Resolves the given URI against this URI.
     * @param uri the uri to resolve against this instance
     * @return this instance (mutated)
     * @see URI#resolve(URI)
     */
    public MutableUri resolve(final MutableUri uri) {
        this.uri = this.uri.resolve(uri.asURI());
        return this;
    }

    @Override
    public String toString() {
        return uri.toString();
    }

    /**
     * Returns the content of this URI as a US-ASCII string.
     * @return the content of this URI as a US-ASCII string.
     */
    public String toASCIIString() {
        return uri.toASCIIString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof MutableUri)) {
            return false;
        }
        MutableUri that = (MutableUri) o;
        return uri.equals(that.uri);

    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }
}
