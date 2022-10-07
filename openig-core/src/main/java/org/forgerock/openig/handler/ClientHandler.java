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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import static java.lang.String.format;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_CONNECT_TIMEOUT;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_HOSTNAME_VERIFIER;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_KEY_MANAGERS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_LOADER;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_MAX_CONNECTIONS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_RETRY_REQUESTS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_REUSE_CONNECTIONS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_SO_TIMEOUT;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_SSLCONTEXT_ALGORITHM;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_SSL_CIPHER_SUITES;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_SSL_ENABLED_PROTOCOLS;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_TEMPORARY_STORAGE;
import static org.forgerock.http.handler.HttpClientHandler.OPTION_TRUST_MANAGERS;
import static org.forgerock.json.JsonValueFunctions.duration;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.json.JsonValueFunctions.listOf;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.forgerock.util.Utils.closeSilently;

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
import org.forgerock.http.protocol.Status;
import org.forgerock.http.spi.Loader;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.Factory;
import org.forgerock.util.Options;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *       "sslCipherSuites": [ "TLS_DH_anon_WITH_AES_256_CBC_SHA256", ... ],
 *       "temporaryStorage": {reference to or inline declaration of a TemporaryStorage}
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
public class ClientHandler implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

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
    	if ("websocket".equalsIgnoreCase(request.getHeaders().getFirst("upgrade"))) {
    		final Response response=new Response(Status.SWITCHING_PROTOCOLS);
    		return Promises.newResultPromise(response);
    	}
        return delegate.handle(context, request)
                       .thenOnResult(new ResultHandler<Response>() {
                           @Override
                           public void handleResult(final Response response) {
                               if (response.getCause() != null) {
                                   logger.warn("An error occurred while processing the request", response.getCause());
                               }
                           }
                       });
    }

    /** Creates and initializes a client handler in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        private static final Logger logger = LoggerFactory.getLogger(Heaplet.class);

        private HttpClientHandler httpClientHandler;

        @SuppressWarnings("unchecked")
        @Override
        public Object create() throws HeapException {
            final Options options = Options.defaultOptions();
            final JsonValue evaluated = config.as(evaluatedWithHeapProperties());

            // Force the HTTP client to be the asynchronous one.
            // We can't rely on the ServiceLoader as it depends on where the JAR files are stored.
            options.set(OPTION_LOADER, new Loader() {
                @Override
                public <S> S load(Class<S> service, Options options) {
                    return service.cast(new AsyncHttpClientProvider());
                }
            });

            if (evaluated.isDefined("connections")) {
                options.set(OPTION_MAX_CONNECTIONS, evaluated.get("connections").asInteger());
            }

            if (evaluated.isDefined("disableReuseConnection")) {
                options.set(OPTION_REUSE_CONNECTIONS, !evaluated.get("disableReuseConnection").asBoolean());
            }

            if (evaluated.isDefined("disableRetries")) {
                options.set(OPTION_RETRY_REQUESTS, !evaluated.get("disableRetries").asBoolean());
            }

            if (evaluated.isDefined("hostnameVerifier")) {
                options.set(OPTION_HOSTNAME_VERIFIER,
                            evaluated.get("hostnameVerifier")
                                     .as(enumConstant(HttpClientHandler.HostnameVerifier.class)));
            }

            if (evaluated.isDefined("sslContextAlgorithm")) {
                options.set(OPTION_SSLCONTEXT_ALGORITHM, evaluated.get("sslContextAlgorithm").asString());
            }

            if (evaluated.isDefined("soTimeout")) {
                options.set(OPTION_SO_TIMEOUT, evaluated.get("soTimeout").as(duration()));
            }

            if (evaluated.isDefined("connectionTimeout")) {
                options.set(OPTION_CONNECT_TIMEOUT, evaluated.get("connectionTimeout").as(duration()));
            }

            if (evaluated.isDefined("sslEnabledProtocols")) {
                options.set(OPTION_SSL_ENABLED_PROTOCOLS,
                            evaluated.get("sslEnabledProtocols").asList(String.class));
            }

            if (evaluated.isDefined("sslCipherSuites")) {
                options.set(OPTION_SSL_CIPHER_SUITES, evaluated.get("sslCipherSuites").asList(String.class));
            }

            if (evaluated.isDefined("numberOfWorkers")) {
                options.set(AsyncHttpClientProvider.OPTION_WORKER_THREADS,
                            evaluated.get("numberOfWorkers").asInteger());
            }

            options.set(OPTION_TEMPORARY_STORAGE, evaluated.get("temporaryStorage")
                                                           .defaultTo(TEMPORARY_STORAGE_HEAP_KEY)
                                                           .as(requiredHeapObject(heap, Factory.class)));

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
                    managers.addAll(trustManagerConfig.as(listOf(requiredHeapObject(heap, TrustManager.class))));
                } else {
                    managers.add(trustManagerConfig.as(requiredHeapObject(heap, TrustManager.class)));
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
                    managers.addAll(keyManagerConfig.as(listOf(requiredHeapObject(heap, KeyManager.class))));
                } else {
                    managers.add(keyManagerConfig.as(requiredHeapObject(heap, KeyManager.class)));
                }
                keyManagers = managers.toArray(new KeyManager[managers.size()]);
            }
            return keyManagers;
        }
    }
}
