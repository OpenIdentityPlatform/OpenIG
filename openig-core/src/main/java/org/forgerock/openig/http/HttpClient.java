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
 * Copyright 2009 Sun Microsystems Inc. All rights reserved.
 * Portions Copyrighted 2010–2011 ApexIdentity Inc.
 * Portions Copyrighted 2011-2014 ForgeRock, Inc.
 */

package org.forgerock.openig.http;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.protocol.RequestAddCookies;
import org.apache.http.client.protocol.RequestProxyAuthentication;
import org.apache.http.client.protocol.RequestTargetAuthentication;
import org.apache.http.client.protocol.ResponseProcessCookies;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.forgerock.openig.header.ConnectionHeader;
import org.forgerock.openig.header.ContentEncodingHeader;
import org.forgerock.openig.header.ContentLengthHeader;
import org.forgerock.openig.header.ContentTypeHeader;
import org.forgerock.openig.io.BranchingStreamWrapper;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.forgerock.openig.util.NoRetryHttpRequestRetryHandler;

/**
 * Submits requests to remote servers. In this implementation, requests are
 * dispatched through the <a href="http://hc.apache.org/">Apache
 * HttpComponents</a> client.
 * <p>
 * <strong>Note:</strong> This implementation does not verify hostnames for
 * outgoing SSL connections. This is because the gateway will usually access the
 * SSL endpoint using a raw IP address rather than a fully-qualified hostname.
 */
public class HttpClient {
    /** A request that encloses an entity. */
    private static class EntityRequest extends HttpEntityEnclosingRequestBase {
        private final String method;

        public EntityRequest(final Request request) {
            this.method = request.method;
            final InputStreamEntity entity =
                    new InputStreamEntity(request.entity, new ContentLengthHeader(request).length);
            entity.setContentType(new ContentTypeHeader(request).toString());
            entity.setContentEncoding(new ContentEncodingHeader(request).toString());
            setEntity(entity);
        }

        @Override
        public String getMethod() {
            return method;
        }
    }

    /** A request that does not enclose an entity. */
    private static class NonEntityRequest extends HttpRequestBase {
        private final String method;

        public NonEntityRequest(final Request request) {
            this.method = request.method;
            final Header contentLengthHeader[] = getHeaders(ContentLengthHeader.NAME);
            if ((contentLengthHeader == null || contentLengthHeader.length == 0)
                    && ("PUT".equals(method) || "POST".equals(method) || "PROPFIND".equals(method))) {
                setHeader(ContentLengthHeader.NAME, "0");
            }
        }

        @Override
        public String getMethod() {
            return method;
        }
    }

    /** Default maximum number of collections through HTTP client. */
    private static final int DEFAULT_CONNECTIONS = 64;

    /** Headers that are suppressed in request. */
    // FIXME: How should the the "Expect" header be handled?
    private static final CaseInsensitiveSet SUPPRESS_REQUEST_HEADERS = new CaseInsensitiveSet(
            Arrays.asList(
                    // populated in outgoing request by EntityRequest (HttpEntityEnclosingRequestBase):
                    "Content-Encoding", "Content-Length", "Content-Type",
                    // hop-to-hop headers, not forwarded by proxies, per RFC 2616 §13.5.1:
                    "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE",
                    "Trailers", "Transfer-Encoding", "Upgrade"));

    /** Headers that are suppressed in response. */
    private static final CaseInsensitiveSet SUPPRESS_RESPONSE_HEADERS = new CaseInsensitiveSet(
            Arrays.asList(
                    // hop-to-hop headers, not forwarded by proxies, per RFC 2616 §13.5.1:
                    "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE",
                    "Trailers", "Transfer-Encoding", "Upgrade"));

    /**
     * Returns a new SSL socket factory that does not perform hostname
     * verification.
     */
    private static SSLSocketFactory newSSLSocketFactory() {
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
        } catch (final NoSuchAlgorithmException nsae) {
            throw new IllegalStateException(nsae);
        }
        try {
            sslContext.init(null, null, null);
        } catch (final KeyManagementException kme) {
            throw new IllegalStateException(kme);
        }
        final SSLSocketFactory sslSocketFactory = new SSLSocketFactory(sslContext);
        sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        return sslSocketFactory;
    }

    /** The HTTP client to transmit requests through. */
    private final DefaultHttpClient httpClient;

    /**
     * Allocates temporary buffers for caching streamed content during request
     * processing.
     */
    private final TemporaryStorage storage;

    /**
     * Creates a new client handler which will cache at most 64 connections.
     *
     * @param storage
     *            the TemporyStorage to use
     */
    public HttpClient(final TemporaryStorage storage) {
        this(storage, DEFAULT_CONNECTIONS);
    }

    /**
     * Creates a new client handler with the specified maximum number of cached
     * connections.
     *
     * @param storage
     *            the TemporyStorage to use
     * @param connections
     *            the maximum number of connections to open.
     */
    public HttpClient(final TemporaryStorage storage, final int connections) {
        this.storage = storage;
        final BasicHttpParams parameters = new BasicHttpParams();
        final int maxConnections = connections <= 0 ? DEFAULT_CONNECTIONS : connections;
        ConnManagerParams.setMaxTotalConnections(parameters, maxConnections);
        ConnManagerParams.setMaxConnectionsPerRoute(parameters,
                new ConnPerRouteBean(maxConnections));
        HttpProtocolParams.setVersion(parameters, HttpVersion.HTTP_1_1);
        HttpClientParams.setRedirecting(parameters, false);
        final SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        registry.register(new Scheme("https", newSSLSocketFactory(), 443));
        final ClientConnectionManager connectionManager =
                new ThreadSafeClientConnManager(parameters, registry);
        httpClient = new DefaultHttpClient(connectionManager, parameters);
        httpClient.removeRequestInterceptorByClass(RequestAddCookies.class);
        httpClient.removeRequestInterceptorByClass(RequestProxyAuthentication.class);
        httpClient.removeRequestInterceptorByClass(RequestTargetAuthentication.class);
        httpClient.removeResponseInterceptorByClass(ResponseProcessCookies.class);
        // TODO: set timeout to drop stalled connections?
    }

    /**
     * Disables connection caching.
     *
     * @return this HTTP client.
     */
    public HttpClient disableConnectionReuse() {
        httpClient.setReuseStrategy(new NoConnectionReuseStrategy());
        return this;
    }

    /**
     * Disables automatic retrying of failed requests.
     *
     * @param logger
     *            a logger which should be used for logging the reason that a
     *            request failed.
     * @return this HTTP client.
     */
    public HttpClient disableRetries(final Logger logger) {
        httpClient.setHttpRequestRetryHandler(new NoRetryHttpRequestRetryHandler(logger));
        return this;
    }

    /**
     * Submits the exchange request to the remote server. Creates and populates
     * the exchange response from that provided by the remote server.
     *
     * @param exchange
     *            The HTTP exchange containing the request to send and where the
     *            response will be placed.
     * @throws IOException
     *             If an IO error occurred while performing the request.
     */
    public void execute(final Exchange exchange) throws IOException {
        // recover any previous response connection, if present
        if (exchange.response != null && exchange.response.entity != null) {
            exchange.response.entity.close();
        }
        exchange.response = execute(exchange.request);
    }

    /**
     * Submits the request to the remote server. Creates and populates the
     * response from that provided by the remote server.
     *
     * @param request
     *            The HTTP request to send.
     * @return The HTTP response.
     * @throws IOException
     *             If an IO error occurred while performing the request.
     */
    public Response execute(final Request request) throws IOException {
        final HttpRequestBase clientRequest =
                request.entity != null ? new EntityRequest(request) : new NonEntityRequest(request);
        clientRequest.setURI(request.uri);
        // connection headers to suppress
        final CaseInsensitiveSet suppressConnection = new CaseInsensitiveSet();
        // parse request connection headers to be suppressed in request
        suppressConnection.addAll(new ConnectionHeader(request).tokens);
        // request headers
        for (final String name : request.headers.keySet()) {
            if (!SUPPRESS_REQUEST_HEADERS.contains(name) && !suppressConnection.contains(name)) {
                for (final String value : request.headers.get(name)) {
                    clientRequest.addHeader(name, value);
                }
            }
        }
        // send request
        final HttpResponse clientResponse = httpClient.execute(clientRequest);
        final Response response = new Response();
        // response entity
        final HttpEntity clientResponseEntity = clientResponse.getEntity();
        if (clientResponseEntity != null) {
            response.entity =
                    new BranchingStreamWrapper(clientResponseEntity.getContent(), storage);
        }
        // response status line
        final StatusLine statusLine = clientResponse.getStatusLine();
        response.version = statusLine.getProtocolVersion().toString();
        response.status = statusLine.getStatusCode();
        response.reason = statusLine.getReasonPhrase();
        // parse response connection headers to be suppressed in response
        suppressConnection.clear();
        suppressConnection.addAll(new ConnectionHeader(response).tokens);
        // response headers
        for (final HeaderIterator i = clientResponse.headerIterator(); i.hasNext();) {
            final Header header = i.nextHeader();
            final String name = header.getName();
            if (!SUPPRESS_RESPONSE_HEADERS.contains(name) && !suppressConnection.contains(name)) {
                response.headers.add(name, header.getValue());
            }
        }
        // TODO: decide if need to try-finally to call httpRequest.abort?
        return response;
    }
}
