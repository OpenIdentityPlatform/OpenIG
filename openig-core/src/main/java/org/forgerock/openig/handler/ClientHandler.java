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

package org.forgerock.openig.handler;

import static java.lang.String.format;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_CONNECT_TIMEOUT;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_HOSTNAME_VERIFIER;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_KEY_MANAGERS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_MAX_CONNECTIONS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_RETRY_REQUESTS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_REUSE_CONNECTIONS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_SO_TIMEOUT;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_SSLCONTEXT_ALGORITHM;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_SSL_CIPHER_SUITES;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_SSL_ENABLED_PROTOCOLS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_TEMPORARY_STORAGE;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_TRUST_MANAGERS;
import static org.forgerock.openig.util.JsonValues.ofRequiredHeapObject;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.time.Duration.duration;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.apache.async.AsyncHttpClientProvider;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.Options;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.time.Duration;

/**
 * Submits requests to remote servers. In this implementation, requests are
 * dispatched through a CHF {@link org.forgerock.http.spi.HttpClient}.
 *
 *
 * <pre>
 *   {@code
 *   {
 *     "name": "ClientHandler",
 *     "type": "ClientHandler",
 *     "config": {
 *       "connections": 64,
 *       "disableReuseConnection": true,
 *       "disableRetries": true,
 *       "hostnameVerifier": "ALLOW_ALL",
 *       "sslContextAlgorithm": "TLS",
 *       "soTimeout": "10 seconds",
 *       "connectionTimeout": "10 seconds",
 *       "numberOfWorkers": 6,
 *       "keyManager": [ "RefToKeyManager", ... ],
 *       "trustManager": [ "RefToTrustManager", ... ],
 *       "sslEnabledProtocols": [ "SSLv2", ... ],
 *       "sslCipherSuites": [ "TLS_DH_anon_WITH_AES_256_CBC_SHA256", ... ]
 *     }
 *   }
 *   }
 * </pre>
 *
 * <strong>Note:</strong> This implementation does not verify hostnames for
 * outgoing SSL connections by default. This is because the gateway will usually access the
 * SSL endpoint using a raw IP address rather than a fully-qualified hostname.
 * <br>
 * It's possible to override this behavior using the {@literal hostnameVerifier} attribute (case is not important,
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
 * The {@literal keyManager} and {@literal trustManager} optional attributes are referencing a
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
 * <p>The {@literal numberOfWorkers} optional attribute specifies the number of threads dedicated to process outgoing
 * requests. It defaults to the number of CPUs available to the JVM. This attribute is only used if an asynchronous
 * Http client engine is used (that is the default).
 *
 * <p>The {@literal sslEnabledProtocols} optional attribute specifies
 * <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#jssenames">the protocol
 * versions</a> to be enabled for use on the connection.
 *
 * <p>The {@literal sslCipherSuites} optional attribute specifies
 * <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#ciphersuites">
 * cipher suite names</a> used by the SSL connection.
 *
 * @see Duration
 * @see org.forgerock.openig.security.KeyManagerHeaplet
 * @see org.forgerock.openig.security.TrustManagerHeaplet
 */
public class ClientHandler extends GenericHeapObject implements Handler {

    private final Handler delegate;

    /**
     * Creates a new client handler.
     *
     * @param delegate
     *         The HTTP Handler delegate.
     */
    public ClientHandler(final Handler delegate) {
        this.delegate = delegate;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        return delegate.handle(context, request)
                       .thenOnResult(new ResultHandler<Response>() {
                           @Override
                           public void handleResult(final Response response) {
                               if (response.getCause() != null) {
                                   logger.warning(response.getCause());
                               }
                           }
                       });
    }

    /** Creates and initializes a client handler in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        private HttpClientHandler httpClientHandler;

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
                                                            .asEnum(HttpClientHandler.HostnameVerifier.class));
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

            if (config.isDefined("sslEnabledProtocols")) {
                options.set(OPTION_SSL_ENABLED_PROTOCOLS, config.get("sslEnabledProtocols").asList(String.class));
            }

            if (config.isDefined("sslCipherSuites")) {
                options.set(OPTION_SSL_CIPHER_SUITES, config.get("sslCipherSuites").asList(String.class));
            }

            if (config.isDefined("numberOfWorkers")) {
                options.set(AsyncHttpClientProvider.OPTION_WORKER_THREADS,
                            config.get("numberOfWorkers").asInteger());
            }

            options.set(OPTION_TEMPORARY_STORAGE, storage);
            options.set(OPTION_KEY_MANAGERS, getKeyManagers());
            options.set(OPTION_TRUST_MANAGERS, getTrustManagers());

            try {
                httpClientHandler = new HttpClientHandler(options);
                return new ClientHandler(httpClientHandler);
            } catch (final HttpApplicationException e) {
                throw new HeapException(format("Cannot build ClientHandler named '%s'", name), e);
            }
        }

        @Override
        public void destroy() {
            if (httpClientHandler != null) {
                closeSilently(httpClientHandler);
            }
            super.destroy();
        }

        private TrustManager[] getTrustManagers() throws HeapException {
            // Build an optional TrustManagerFactory
            TrustManager[] trustManagers = null;
            // Uses TrustManager references
            if (config.isDefined("trustManager")) {
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

        private KeyManager[] getKeyManagers() throws HeapException {
            // Build an optional KeyManagerFactory
            KeyManager[] keyManagers = null;

            // Uses KeyManager references
            if (config.isDefined("keyManager")) {
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
    }
}
