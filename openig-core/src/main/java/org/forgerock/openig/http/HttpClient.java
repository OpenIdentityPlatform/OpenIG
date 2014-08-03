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

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.forgerock.openig.util.Duration.duration;
import static org.forgerock.openig.util.JsonValueUtil.*;
import static org.forgerock.util.Utils.closeSilently;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

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
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.header.ConnectionHeader;
import org.forgerock.openig.header.ContentEncodingHeader;
import org.forgerock.openig.header.ContentLengthHeader;
import org.forgerock.openig.header.ContentTypeHeader;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.io.BranchingStreamWrapper;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.forgerock.openig.util.Duration;
import org.forgerock.openig.util.NoRetryHttpRequestRetryHandler;

/**
 * Submits requests to remote servers. In this implementation, requests are
 * dispatched through the <a href="http://hc.apache.org/">Apache
 * HttpComponents</a> client.
 * <p>
 * <pre>
 *   {
 *     "name": "HttpClient",
 *     "type": "HttpClient",
 *     "config": {
 *       "connections": 64,
 *       "disableReuseConnection": true,
 *       "disableRetries": true,
 *       "hostnameVerifier": "ALLOW_ALL",
 *       "soTimeout": "10 seconds",
 *       "connectionTimeout": "10 seconds",
 *       "keystore": {
 *           "file": "/path/to/keystore.jks",
 *           "password": "changeit"
 *       },
 *       "truststore": {
 *           "file": "/path/to/keystore.jks",
 *           "password": "changeit"
 *       }
 *     }
 *   }
 * </pre>
 * <p>
 * <strong>Note:</strong> This implementation does not verify hostnames for
 * outgoing SSL connections by default. This is because the gateway will usually access the
 * SSL endpoint using a raw IP address rather than a fully-qualified hostname.
 * <p>
 * It's possible to override that behavior using the {@literal hostnameVerifier} attribute (case is not important,
 * but unknown values will produce an error).
 * <p>
 * Accepted values are:
 * <ul>
 *     <li>{@literal ALLOW_ALL} (the default)</li>
 *     <li>{@literal BROWSER_COMPATIBLE}</li>
 *     <li>{@literal STRICT}</li>
 * </ul>
 * <p>
 * The {@literal keystore} and {@literal truststore} optional attributes are both supporting the following attributes:
 * <ul>
 *     <li>{@literal file}: path to the key store</li>
 *     <li>{@literal type}: key store type (defaults to {@literal JKS})</li>
 *     <li>{@literal alg}: certificate algorithm to use (defaults to {@literal SunX509})</li>
 *     <li>{@literal password}: mandatory for key store, optional for trust store, defined as an {@link Expression}</li>
 * </ul>
 * <p>
 * The {@literal soTimeout} optional attribute specifies a socket timeout (the given amount of time a connection
 * will live before being considered a stalled and automatically destroyed). It defaults to {@literal 10 seconds}.
 * <p>
 * The {@literal connectionTimeout} optional attribute specifies a connection timeout (the given amount of time to
 * wait until the connection is established). It defaults to {@literal 10 seconds}.
 *
 * @see Duration
 */
public class HttpClient {

    /**
     * Key to retrieve an {@link HttpClient} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String HTTP_CLIENT_HEAP_KEY = "HttpClient";

    /** Reuse of Http connection is disabled by default. */
    public static final boolean DISABLE_CONNECTION_REUSE = false;

    /** Http connection retries are disabled by default. */
    public static final boolean DISABLE_RETRIES = false;

    /** Default maximum number of collections through HTTP client. */
    public static final int DEFAULT_CONNECTIONS = 64;

    /**
     * Value of the default timeout.
     */
    public static final String TEN_SECONDS = "10 seconds";

    /**
     * Default socket timeout as a {@link Duration}.
     */
    public static final Duration DEFAULT_SO_TIMEOUT = duration(TEN_SECONDS);

    /**
     * Default connection timeout as a {@link Duration}.
     */
    public static final Duration DEFAULT_CONNECTION_TIMEOUT = duration(TEN_SECONDS);

    /** A request that encloses an entity. */
    private static class EntityRequest extends HttpEntityEnclosingRequestBase {
        private final String method;

        public EntityRequest(final Request request) {
            this.method = request.getMethod();
            final InputStreamEntity entity =
                    new InputStreamEntity(request.getEntity().getRawInputStream(),
                            new ContentLengthHeader(request).getLength());
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
            this.method = request.getMethod();
            final Header[] contentLengthHeader = getHeaders(ContentLengthHeader.NAME);
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

    /**
     * The set of accepted configuration values for the {@literal hostnameVerifier} attribute.
     */
    private static enum Verifier {
        ALLOW_ALL {
            @Override
            X509HostnameVerifier getHostnameVerifier() {
                return SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
            }
        },
        BROWSER_COMPATIBLE {
            @Override
            X509HostnameVerifier getHostnameVerifier() {
                return SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
            }
        },
        STRICT {
            @Override
            X509HostnameVerifier getHostnameVerifier() {
                return SSLSocketFactory.STRICT_HOSTNAME_VERIFIER;
            }
        };

        abstract X509HostnameVerifier getHostnameVerifier();
    }

    /** Headers that are suppressed in request. */
    // FIXME: How should the the "Expect" header be handled?
    private static final CaseInsensitiveSet SUPPRESS_REQUEST_HEADERS = new CaseInsensitiveSet(
            Arrays.asList(
                    // populated in outgoing request by EntityRequest (HttpEntityEnclosingRequestBase):
                    "Content-Encoding", "Content-Length", "Content-Type",
                    // hop-to-hop headers, not forwarded by proxies, per RFC 2616 §13.5.1:
                    "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE",
                    "Trailers", "Transfer-Encoding", "Upgrade")
    );

    /** Headers that are suppressed in response. */
    private static final CaseInsensitiveSet SUPPRESS_RESPONSE_HEADERS = new CaseInsensitiveSet(
            Arrays.asList(
                    // hop-to-hop headers, not forwarded by proxies, per RFC 2616 §13.5.1:
                    "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE",
                    "Trailers", "Transfer-Encoding", "Upgrade")
    );

    /**
     * Returns a new SSL socket factory that does not perform hostname verification.
     *
     * @param keyManagerFactory
     *         Provides Keys/Certificates in case of SSL/TLS connections
     * @param trustManagerFactory
     *         Provides TrustManagers in case of SSL/TLS connections
     * @param hostnameVerifier hostname verification strategy
     * @throws GeneralSecurityException
     *         if the SSL algorithm is unsupported or if an error occurs during SSL configuration
     */
    private static SSLSocketFactory newSSLSocketFactory(final KeyManagerFactory keyManagerFactory,
                                                        final TrustManagerFactory trustManagerFactory,
                                                        final X509HostnameVerifier hostnameVerifier)
            throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init((keyManagerFactory == null) ? null : keyManagerFactory.getKeyManagers(),
                     (trustManagerFactory == null) ? null : trustManagerFactory.getTrustManagers(),
                     null);
        SSLSocketFactory factory = new SSLSocketFactory(context);
        factory.setHostnameVerifier(hostnameVerifier);
        return factory;
    }

    /** The HTTP client to transmit requests through. */
    private final DefaultHttpClient httpClient;
    /**
     * Allocates temporary buffers for caching streamed content during request
     * processing.
     */
    private final TemporaryStorage storage;

    /**
     * Creates a new client handler which will cache at most 64 connections, allow all host names for SSL requests
     * and has a both a default connection and so timeout.
     *
     * @param storage the TemporaryStorage to use
     * @throws GeneralSecurityException
     *         if the SSL algorithm is unsupported or if an error occurs during SSL configuration
     */
    public HttpClient(final TemporaryStorage storage) throws GeneralSecurityException {
        this(storage,
             DEFAULT_CONNECTIONS,
             null,
             null,
             Verifier.ALLOW_ALL,
             DEFAULT_SO_TIMEOUT,
             DEFAULT_CONNECTION_TIMEOUT);
    }

    /**
     * Creates a new client handler with the specified maximum number of cached connections.
     *
     * @param storage the {@link TemporaryStorage} to use
     * @param connections the maximum number of connections to open.
     * @param keyManagerFactory Provides Keys/Certificates in case of SSL/TLS connections
     * @param trustManagerFactory Provides TrustManagers in case of SSL/TLS connections
     * @param verifier hostname verification strategy
     * @param soTimeout socket timeout duration
     * @param connectionTimeout connection timeout duration
     * @throws GeneralSecurityException
     *         if the SSL algorithm is unsupported or if an error occurs during SSL configuration
     */
    public HttpClient(final TemporaryStorage storage,
                      final int connections,
                      final KeyManagerFactory keyManagerFactory,
                      final TrustManagerFactory trustManagerFactory,
                      final Verifier verifier,
                      final Duration soTimeout,
                      final Duration connectionTimeout) throws GeneralSecurityException {
        this.storage = storage;

        final BasicHttpParams parameters = new BasicHttpParams();
        final int maxConnections = connections <= 0 ? DEFAULT_CONNECTIONS : connections;
        ConnManagerParams.setMaxTotalConnections(parameters, maxConnections);
        ConnManagerParams.setMaxConnectionsPerRoute(parameters, new ConnPerRouteBean(maxConnections));
        if (!soTimeout.isUnlimited()) {
            HttpConnectionParams.setSoTimeout(parameters, (int) soTimeout.to(MILLISECONDS));
        }
        if (!connectionTimeout.isUnlimited()) {
            HttpConnectionParams.setConnectionTimeout(parameters, (int) connectionTimeout.to(MILLISECONDS));
        }
        HttpProtocolParams.setVersion(parameters, HttpVersion.HTTP_1_1);
        HttpClientParams.setRedirecting(parameters, false);

        final SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        registry.register(new Scheme("https",
                                     newSSLSocketFactory(keyManagerFactory,
                                                         trustManagerFactory,
                                                         verifier.getHostnameVerifier()),
                                     443));
        final ClientConnectionManager connectionManager = new ThreadSafeClientConnManager(parameters, registry);

        httpClient = new DefaultHttpClient(connectionManager, parameters);
        httpClient.removeRequestInterceptorByClass(RequestAddCookies.class);
        httpClient.removeRequestInterceptorByClass(RequestProxyAuthentication.class);
        httpClient.removeRequestInterceptorByClass(RequestTargetAuthentication.class);
        httpClient.removeResponseInterceptorByClass(ResponseProcessCookies.class);
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
     * @param logger a logger which should be used for logging the reason that a
     * request failed.
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
     * @param exchange The HTTP exchange containing the request to send and where the
     * response will be placed.
     * @throws IOException If an IO error occurred while performing the request.
     */
    public void execute(final Exchange exchange) throws IOException {
        // recover any previous response connection, if present
        closeSilently(exchange.response);
        exchange.response = execute(exchange.request);
    }

    /**
     * Submits the request to the remote server. Creates and populates the
     * response from that provided by the remote server.
     *
     * @param request The HTTP request to send.
     * @return The HTTP response.
     * @throws IOException If an IO error occurred while performing the request.
     */
    public Response execute(final Request request) throws IOException {
        final HttpRequestBase clientRequest =
                request.getEntity() != null ? new EntityRequest(request) : new NonEntityRequest(request);
        clientRequest.setURI(request.getUri());
        // connection headers to suppress
        final CaseInsensitiveSet suppressConnection = new CaseInsensitiveSet();
        // parse request connection headers to be suppressed in request
        suppressConnection.addAll(new ConnectionHeader(request).getTokens());
        // request headers
        for (final String name : request.getHeaders().keySet()) {
            if (!SUPPRESS_REQUEST_HEADERS.contains(name) && !suppressConnection.contains(name)) {
                for (final String value : request.getHeaders().get(name)) {
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
            response.setEntity(new BranchingStreamWrapper(clientResponseEntity.getContent(),
                    storage));
        }
        // response status line
        final StatusLine statusLine = clientResponse.getStatusLine();
        response.setVersion(statusLine.getProtocolVersion().toString());
        response.setStatus(statusLine.getStatusCode());
        response.setReason(statusLine.getReasonPhrase());
        // parse response connection headers to be suppressed in response
        suppressConnection.clear();
        suppressConnection.addAll(new ConnectionHeader(response).getTokens());
        // response headers
        for (final HeaderIterator i = clientResponse.headerIterator(); i.hasNext();) {
            final Header header = i.nextHeader();
            final String name = header.getName();
            if (!SUPPRESS_RESPONSE_HEADERS.contains(name) && !suppressConnection.contains(name)) {
                response.getHeaders().add(name, header.getValue());
            }
        }
        // TODO: decide if need to try-finally to call httpRequest.abort?
        return response;
    }

    /**
     * Creates and initializes a http client object in a heap environment.
     */
    public static class Heaplet extends NestedHeaplet {

        @Override
        public Object create() throws HeapException {
            // optional, default to DEFAULT_CONNECTIONS number of connections
            Integer connections = config.get("connections").defaultTo(DEFAULT_CONNECTIONS).asInteger();
            // determines if connections should be reused, disables keep-alive
            Boolean disableReuseConnection = config.get("disableReuseConnection")
                                                   .defaultTo(DISABLE_CONNECTION_REUSE)
                                                   .asBoolean();
            // determines if requests should be retried on failure
            Boolean disableRetries = config.get("disableRetries").defaultTo(DISABLE_RETRIES).asBoolean();

            // Build an optional KeyManagerFactory
            KeyManagerFactory keyManagerFactory = null;
            if (config.isDefined("keystore")) {
                JsonValue store = config.get("keystore");
                File keystoreFile = store.get("file").required().asFile();
                String password = evaluate(store.get("password").required());
                String type = store.get("type").defaultTo("JKS").asString().toUpperCase();
                String algorithm = store.get("alg").defaultTo("SunX509").asString();

                keyManagerFactory = buildKeyManagerFactory(keystoreFile, type, algorithm, password);
            }

            // Build an optional TrustManagerFactory
            TrustManagerFactory trustManagerFactory = null;
            if (config.isDefined("truststore")) {
                JsonValue store = config.get("truststore");
                File truststoreFile = store.get("file").required().asFile();

                // Password is optional for trust store
                String password = evaluate(store.get("password"));
                String type = store.get("type").defaultTo("JKS").asString().toUpperCase();
                String algorithm = store.get("alg").defaultTo("SunX509").asString();

                trustManagerFactory = buildTrustManagerFactory(truststoreFile, type, algorithm, password);
            }

            Verifier verifier = config.get("hostnameVerifier")
                                      .defaultTo(Verifier.ALLOW_ALL.name())
                                      .asEnum(Verifier.class);

            // Timeouts
            Duration soTimeout = duration(config.get("soTimeout").defaultTo(TEN_SECONDS).asString());
            Duration connectionTimeout = duration(config.get("connectionTimeout")
                                                        .defaultTo(TEN_SECONDS)
                                                        .asString());

            // Create the HttpClient instance
            try {
                HttpClient client = new HttpClient(storage,
                                                   connections,
                                                   keyManagerFactory,
                                                   trustManagerFactory,
                                                   verifier,
                                                   soTimeout,
                                                   connectionTimeout);

                if (disableRetries) {
                    client.disableRetries(logger);
                }
                if (disableReuseConnection) {
                    client.disableConnectionReuse();
                }

                return client;
            } catch (GeneralSecurityException e) {
                throw new HeapException(format("Cannot build HttpClient named '%s'", name), e);
            }
        }

        private TrustManagerFactory buildTrustManagerFactory(final File truststoreFile,
                                                             final String type,
                                                             final String algorithm,
                                                             final String password)
                throws HeapException {
            try {
                TrustManagerFactory factory = TrustManagerFactory.getInstance(algorithm);
                KeyStore store = buildKeyStore(truststoreFile, type, password);
                factory.init(store);
                return factory;
            } catch (Exception e) {
                throw new HeapException(format(
                        "Cannot build TrustManagerFactory[alg:%s] from KeyStore[type:%s] stored in %s",
                        algorithm,
                        type,
                        truststoreFile),
                                        e);
            }
        }

        private KeyManagerFactory buildKeyManagerFactory(final File keystoreFile,
                                                         final String type,
                                                         final String algorithm,
                                                         final String password)
                throws HeapException {
            try {
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
                KeyStore keyStore = buildKeyStore(keystoreFile, type, password);
                keyManagerFactory.init(keyStore, password.toCharArray());
                return keyManagerFactory;
            } catch (Exception e) {
                throw new HeapException(format(
                        "Cannot build KeyManagerFactory[alg:%s] from KeyStore[type:%s] stored in %s",
                        algorithm,
                        type,
                        keystoreFile),
                                        e);
            }
        }

        private KeyStore buildKeyStore(final File keystoreFile, final String type, final String password)
                throws Exception {
            KeyStore keyStore = KeyStore.getInstance(type);
            InputStream keyInput = null;
            try {
                keyInput = new FileInputStream(keystoreFile);
                char[] credentials = (password == null) ? null : password.toCharArray();
                keyStore.load(keyInput, credentials);
            } finally {
                closeSilently(keyInput);
            }
            return keyStore;
        }
    }

}
