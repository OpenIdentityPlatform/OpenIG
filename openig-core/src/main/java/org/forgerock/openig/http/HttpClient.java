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
 * Portions Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static java.lang.String.format;
import static org.forgerock.http.handler.HttpClientHandler.*;
import static org.forgerock.openig.util.JsonValues.evaluate;
import static org.forgerock.openig.util.JsonValues.ofRequiredHeapObject;
import static org.forgerock.openig.util.JsonValues.warnForDeprecation;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.time.Duration.duration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.forgerock.http.Context;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.handler.HttpClientHandler.HostnameVerifier;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.context.RootContext;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.Options;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;

/**
 * Submits requests to remote servers. In this implementation, requests are
 * dispatched through the <a href="http://hc.apache.org/">Apache
 * HttpComponents</a> client.
 *
 *
 * <pre>
 *   {@code
 *   {
 *     "name": "HttpClient",
 *     "type": "HttpClient",
 *     "config": {
 *       "connections": 64,
 *       "disableReuseConnection": true,
 *       "disableRetries": true,
 *       "hostnameVerifier": "ALLOW_ALL",
 *       "sslContextAlgorithm": "TLS",
 *       "soTimeout": "10 seconds",
 *       "connectionTimeout": "10 seconds",
 *       "keystore": {
 *           "file": "/path/to/keystore.jks",
 *           "password": "changeit"
 *       },
 *       "truststore": {
 *           "file": "/path/to/keystore.jks",
 *           "password": "changeit"
 *       },
 *       "keyManager": [ "RefToKeyManager", ... ]
 *       "trustManager": [ "RefToTrustManager", ... ]
 *     }
 *   }
 *   }
 * </pre>
 *
 * <strong>Note:</strong> This implementation does not verify hostnames for
 * outgoing SSL connections by default. This is because the gateway will usually access the
 * SSL endpoint using a raw IP address rather than a fully-qualified hostname.
 * <br>
 * It's possible to override that behavior using the {@literal hostnameVerifier} attribute (case is not important,
 * but unknown values will produce an error).
 * <br>
 * Accepted values are:
 * <ul>
 *     <li>{@literal ALLOW_ALL} (the default)</li>
 *     <li>{@literal STRICT}</li>
 * </ul>
 * <br>
 * The {@literal sslContextAlgorithm} optional attribute used to set the SSL Context Algorithm for SSL/TLS
 * connections, it defaults to {@literal TLS}. See the JavaSE docs for the full list of supported
 * <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#SSLContext">values.</a>
 * <br>
 * The <strong>deprecated</strong> {@literal keystore} and {@literal truststore} optional attributes are both
 * supporting the following attributes:
 * <ul>
 *     <li>{@literal file}: path to the key store</li>
 *     <li>{@literal type}: key store type (defaults to {@literal JKS})</li>
 *     <li>{@literal alg}: certificate algorithm to use (defaults to {@literal SunX509})</li>
 *     <li>{@literal password}: mandatory for key store, optional for trust store, defined as an
 *     {@link org.forgerock.openig.el.Expression}</li>
 * </ul>
 * <br>
 * The new (since OpenIG 3.1) {@literal keyManager} and {@literal trustManager} optional attributes are referencing a
 * list of {@link KeyManager} (and {@link TrustManager} respectively). They support singleton value (use a single
 * reference) as well as multi-valued references (a list):
 * <pre>
 * {@code
 *     "keyManager": "SingleKeyManagerReference",
 *     "trustManager": [ "RefOne", "RefTwo" ]
 * }
 * </pre>
 *
 * The {@literal soTimeout} optional attribute specifies a socket timeout (the given amount of time a connection
 * will live before being considered a stalled and automatically destroyed). It defaults to {@literal 10 seconds}.
 * <br>
 * The {@literal connectionTimeout} optional attribute specifies a connection timeout (the given amount of time to
 * wait until the connection is established). It defaults to {@literal 10 seconds}.
 *
 * @see Duration
 * @see org.forgerock.openig.security.KeyManagerHeaplet
 * @see org.forgerock.openig.security.TrustManagerHeaplet
 */
public class HttpClient extends GenericHeapObject {

    /** Creates and initializes an {@link HttpClient} in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            final Options options = Options.defaultOptions();

            if (config.isDefined("connections")) {
                options.set(OPTION_MAX_CONNECTIONS, config.get("connections").asInteger());
            }

            if (config.isDefined("disableReuseConnection")) {
                options.set(OPTION_REUSE_CONNECTIONS, !config.get("disableReuseConnection").asBoolean());
            }

            if (config.isDefined("disableRetries")) {
                options.set(OPTION_RETRY_REQUESTS, !config.get("disableRetries").asBoolean());
            }

            if (config.isDefined("hostnameVerifier")) {
                options.set(OPTION_HOSTNAME_VERIFIER, config.get("hostnameVerifier")
                        .asEnum(HostnameVerifier.class));
            }

            if (config.isDefined("sslContextAlgorithm")) {
                options.set(OPTION_SSLCONTEXT_ALGORITHM, config.get("sslContextAlgorithm").asString());
            }

            if (config.isDefined("soTimeout")) {
                options.set(OPTION_SO_TIMEOUT, duration(config.get("soTimeout").asString()));
            }

            if (config.isDefined("connectionTimeout")) {
                options.set(OPTION_CONNECT_TIMEOUT, duration(config.get("connectionTimeout").asString()));
            }

            options.set(OPTION_TEMPORARY_STORAGE, storage);
            options.set(OPTION_KEY_MANAGERS, getKeyManagers());
            options.set(OPTION_TRUST_MANAGERS, getTrustManagers());

            // Create the HttpClient instance
            try {
                return new HttpClient(new HttpClientHandler(options));
            } catch (final HttpApplicationException e) {
                throw new HeapException(format("Cannot build HttpClient named '%s'", name), e);
            }
        }

        private KeyManagerFactory buildKeyManagerFactory(final File keystoreFile,
                final String type, final String algorithm, final String password)
                throws HeapException {
            try {
                final KeyManagerFactory keyManagerFactory =
                        KeyManagerFactory.getInstance(algorithm);
                final KeyStore keyStore = buildKeyStore(keystoreFile, type, password);
                keyManagerFactory.init(keyStore, password.toCharArray());
                return keyManagerFactory;
            } catch (final Exception e) {
                throw new HeapException(
                        format("Cannot build KeyManagerFactory[alg:%s] from KeyStore[type:%s] stored in %s",
                                algorithm, type, keystoreFile), e);
            }
        }

        private KeyStore buildKeyStore(final File keystoreFile, final String type,
                final String password) throws Exception {
            final KeyStore keyStore = KeyStore.getInstance(type);
            InputStream keyInput = null;
            try {
                keyInput = new FileInputStream(keystoreFile);
                final char[] credentials = password == null ? null : password.toCharArray();
                keyStore.load(keyInput, credentials);
            } finally {
                closeSilently(keyInput);
            }
            return keyStore;
        }

        private TrustManagerFactory buildTrustManagerFactory(final File truststoreFile,
                final String type, final String algorithm, final String password)
                throws HeapException {
            try {
                final TrustManagerFactory factory = TrustManagerFactory.getInstance(algorithm);
                final KeyStore store = buildKeyStore(truststoreFile, type, password);
                factory.init(store);
                return factory;
            } catch (final Exception e) {
                throw new HeapException(
                        format("Cannot build TrustManagerFactory[alg:%s] from KeyStore[type:%s] stored in %s",
                                algorithm, type, truststoreFile), e);
            }
        }

        private KeyManager[] getKeyManagers() throws HeapException {
            // Build an optional KeyManagerFactory
            KeyManager[] keyManagers = null;
            if (config.isDefined("keystore")) {
                // This attribute is deprecated: warn the user
                warnForDeprecation(config, logger, "keyManager", "keystore");

                final JsonValue store = config.get("keystore");
                final File keystoreFile = store.get("file").required().asFile();
                final String password = evaluate(store.get("password").required());
                final String type = store.get("type").defaultTo("JKS").asString().toUpperCase();
                final String algorithm = store.get("alg").defaultTo("SunX509").asString();

                keyManagers =
                        buildKeyManagerFactory(keystoreFile, type, algorithm, password)
                                .getKeyManagers();
            }

            // Uses KeyManager references
            if (config.isDefined("keyManager")) {
                if (keyManagers != null) {
                    logger.warning("Cannot use both 'keystore' and 'keyManager' attributes, "
                            + "will use configuration from 'keyManager' attribute");
                }
                final JsonValue keyManagerConfig = config.get("keyManager");
                final List<KeyManager> managers = new ArrayList<>();
                if (keyManagerConfig.isList()) {
                    managers.addAll(keyManagerConfig.asList(ofRequiredHeapObject(heap,
                            KeyManager.class)));
                } else {
                    managers.add(heap.resolve(keyManagerConfig, KeyManager.class));
                }
                keyManagers = managers.toArray(new KeyManager[managers.size()]);
            }
            return keyManagers;
        }

        @Override
        public void destroy() {
            ((HttpClient) this.object).shutdown();
            super.destroy();
        }

        private TrustManager[] getTrustManagers() throws HeapException {
            // Build an optional TrustManagerFactory
            TrustManager[] trustManagers = null;
            if (config.isDefined("truststore")) {
                // This attribute is deprecated: warn the user
                warnForDeprecation(config, logger, "trustManager", "truststore");

                final JsonValue store = config.get("truststore");
                final File truststoreFile = store.get("file").required().asFile();

                // Password is optional for trust store
                final String password = evaluate(store.get("password"));
                final String type = store.get("type").defaultTo("JKS").asString().toUpperCase();
                final String algorithm = store.get("alg").defaultTo("SunX509").asString();

                trustManagers =
                        buildTrustManagerFactory(truststoreFile, type, algorithm, password)
                                .getTrustManagers();
            }

            // Uses TrustManager references
            if (config.isDefined("trustManager")) {
                if (trustManagers != null) {
                    logger.warning("Cannot use both 'truststore' and 'trustManager' attributes, "
                            + "will use configuration from 'trustManager' attribute");
                }
                final JsonValue trustManagerConfig = config.get("trustManager");
                final List<TrustManager> managers = new ArrayList<>();
                if (trustManagerConfig.isList()) {
                    managers.addAll(trustManagerConfig.asList(ofRequiredHeapObject(heap,
                            TrustManager.class)));
                } else {
                    managers.add(heap.resolve(trustManagerConfig, TrustManager.class));
                }
                trustManagers = managers.toArray(new TrustManager[managers.size()]);
            }
            return trustManagers;
        }
    }

    /** The HTTP client handler to which requests will be delegated. */
    private final HttpClientHandler client;

    /** Dummy root context to send with each request. */
    private final Context context = new RootContext();

    /**
     * Creates a new {@code HttpClient} using the provided commons HTTP client
     * handler.
     *
     * @param client
     *            The commons HTTP client handler.
     */
    public HttpClient(final HttpClientHandler client) {
        this.client = client;
    }

    /**
     * Creates a new {@code HttpClient} using a commons HTTP client handler with
     * default settings.
     *
     * @throws HttpApplicationException
     *             If no HTTP client provider could be found.
     */
    public HttpClient() throws HttpApplicationException {
        this(new HttpClientHandler());
    }

    /**
     * Submits the exchange request to the remote server. Creates and populates
     * the exchange.response from that provided by the remote server.
     *
     * @param exchange
     *            The HTTP exchange containing the request to send and where the
     *            response will be placed.
     */
    public void execute(final Exchange exchange) {
        // recover any previous response connection, if present
        closeSilently(exchange.getResponse());
        exchange.setResponse(execute(exchange.getRequest()));
    }

    /**
     * Submits the request to the remote server. Creates and populates the
     * response from that provided by the remote server.
     *
     * @param request
     *            The HTTP request to send.
     * @return The HTTP response.
     */
    public Response execute(final Request request) {
        try {
            return executeAsync(request).getOrThrow();
        } catch (final InterruptedException e) {
            // FIXME: is a 408 time out the best status code?
            return new Response().setStatus(Status.REQUEST_TIMEOUT);
        }
    }

    /**
     * Submits asynchronously the request to the remote server. Creates and populates the
     * response from that provided by the remote server.
     *
     * @param request
     *            The HTTP request to send.
     * @return The promise of the HTTP response.
     */
    public Promise<Response, NeverThrowsException> executeAsync(final Request request) {
        return client.handle(context, request);
    }

    /**
     * Shutdowns the HttpClient means this is not possible anymore to execute any request.
     */
    public void shutdown() {
        closeSilently(client);
    }
}
