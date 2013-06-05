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
 * Copyright © 2009 Sun Microsystems Inc. All rights reserved.
 */

 /*
 * Portions Copyrighted 2010–2011 ApexIdentity Inc.
 * Portions Copyrighted 2011-2013 ForgeRock, Inc.
 */

package org.forgerock.openig.handler;

// Java SE
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;

// Apache HttpComponents
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

// JSON Fluent
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
import org.forgerock.openig.header.ConnectionHeader;
import org.forgerock.openig.header.ContentEncodingHeader;
import org.forgerock.openig.header.ContentLengthHeader;
import org.forgerock.openig.header.ContentTypeHeader;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.io.BranchingStreamWrapper;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.forgerock.openig.util.NoRetryHttpRequestRetryHandler;

/**
 * Submits exchange requests to remote servers. In this implementation, requests are dispatched
 * through the <a href="http://hc.apache.org/">Apache HttpComponents</a> client.
 * <p>
 * <strong>Note:</strong> This handler does not verify hostnames for outgoing SSL connections.
 * This is because the gateway will usually access the SSL endpoint using a raw IP address
 * rather than a fully-qualified hostname.
 *
 * @author Paul C. Bryan
 */
public class ClientHandler extends GenericHandler {

    /** Default maximum number of collections through HTTP client. */
    private static final int DEFAULT_CONNECTIONS = 64;

    /** Headers that are suppressed in request. */
// FIXME: How should the the "Expect" header be handled?
    private static final CaseInsensitiveSet SUPPRESS_REQUEST_HEADERS = new CaseInsensitiveSet(Arrays.asList(
     // populated in outgoing request by EntityRequest (HttpEntityEnclosingRequestBase):
     "Content-Encoding", "Content-Length", "Content-Type",
     // hop-to-hop headers, not forwarded by proxies, per RFC 2616 §13.5.1:
     "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE",
     "Trailers", "Transfer-Encoding", "Upgrade"
    ));

    /** Headers that are suppressed in response. */
    private static final CaseInsensitiveSet SUPPRESS_RESPONSE_HEADERS = new CaseInsensitiveSet(Arrays.asList(
     // hop-to-hop headers, not forwarded by proxies, per RFC 2616 §13.5.1:
     "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE",
     "Trailers", "Transfer-Encoding", "Upgrade"
    ));

    /** Delimiter to split tokens within the Connection header. */
// FIXME: custom connection header—delimiter here is too simplistic
    private static final Pattern DELIM_TOKEN = Pattern.compile("[,\\s]+");

    /** The HTTP client to transmit requests through. */
    private DefaultHttpClient httpClient;

    /** Allocates temporary buffers for caching streamed content during request processing. */
    protected TemporaryStorage storage;

    /**
     * Creates a new client handler with the specified maximum number of
     * connections.
     *
     * @param connections the maximum number of connections to open.
     * @param storage the TemporyStorage to use
     * @param disableReuseConnection if true, connections will not be reused
     * @param disableRetries if true, do not retry a failed request
     */
    public ClientHandler(int connections, TemporaryStorage storage, boolean disableReuseConnection, boolean disableRetries) {
        this.storage = storage;
        BasicHttpParams parameters = new BasicHttpParams();
        ConnManagerParams.setMaxTotalConnections(parameters, connections);
        ConnManagerParams.setMaxConnectionsPerRoute(parameters, new ConnPerRouteBean(connections));
        HttpProtocolParams.setVersion(parameters, HttpVersion.HTTP_1_1);
        HttpClientParams.setRedirecting(parameters, false);
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        registry.register(new Scheme("https", newSSLSocketFactory(), 443));
        ClientConnectionManager connectionManager = new ThreadSafeClientConnManager(parameters, registry);
        httpClient = new DefaultHttpClient(connectionManager, parameters);
        httpClient.removeRequestInterceptorByClass(RequestAddCookies.class);
        httpClient.removeRequestInterceptorByClass(RequestProxyAuthentication.class);
        httpClient.removeRequestInterceptorByClass(RequestTargetAuthentication.class);
        httpClient.removeResponseInterceptorByClass(ResponseProcessCookies.class);
        if (disableReuseConnection) {
            httpClient.setReuseStrategy(new NoConnectionReuseStrategy());
        }
        if (disableRetries) {
            httpClient.setHttpRequestRetryHandler(new NoRetryHttpRequestRetryHandler(logger));
        }
// TODO: set timeout to drop stalled connections?
    }

    /**
     * Returns a new SSL socket factory that does not perform hostname verification.
     */
    private static SSLSocketFactory newSSLSocketFactory() {
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException nsae) {
            throw new IllegalStateException(nsae);
        }
        try {
            sslContext.init(null, null, null);
        } catch (KeyManagementException kme) {
            throw new IllegalStateException(kme);
        }
        SSLSocketFactory sslSocketFactory = new SSLSocketFactory(sslContext);
        sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        return sslSocketFactory;
    }

    /**
     * Submits the exchange request to the remote server. Creates and populates the exchange
     * response from that provided by the remote server.
     */
    @Override
    public void handle(Exchange exchange) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        // recover any previous response connection, if present
        if (exchange.response != null && exchange.response.entity != null) {
            exchange.response.entity.close();
        }
        HttpRequestBase clientRequest = (exchange.request.entity != null ?
         new EntityRequest(exchange.request) : new NonEntityRequest(exchange.request));
        clientRequest.setURI(exchange.request.uri);
        // connection headers to suppress
        CaseInsensitiveSet suppressConnection = new CaseInsensitiveSet();
        // parse request connection headers to be suppressed in request
        suppressConnection.clear();
        suppressConnection.addAll(new ConnectionHeader(exchange.request).tokens);
        // request headers
        for (String name : exchange.request.headers.keySet()) {
            if (!SUPPRESS_REQUEST_HEADERS.contains(name) && !suppressConnection.contains(name)) {
                for (String value : exchange.request.headers.get(name)) {
                    clientRequest.addHeader(name, value);
                }
            }
        }
        // send request
        HttpResponse clientResponse = httpClient.execute(clientRequest);
        exchange.response = new Response();
        // response entity
        HttpEntity clientResponseEntity = clientResponse.getEntity();
        if (clientResponseEntity != null) {
            exchange.response.entity = new BranchingStreamWrapper(clientResponseEntity.getContent(), storage);
        }
        // response status line
        StatusLine statusLine = clientResponse.getStatusLine();
        exchange.response.version = statusLine.getProtocolVersion().toString();
        exchange.response.status = statusLine.getStatusCode();
        exchange.response.reason = statusLine.getReasonPhrase();
        // parse response connection headers to be suppressed in response
        suppressConnection.clear();
        suppressConnection.addAll(new ConnectionHeader(exchange.response).tokens);
        // response headers
        for (HeaderIterator i = clientResponse.headerIterator(); i.hasNext();) {
            Header header = i.nextHeader();
            String name = header.getName();
            if (!SUPPRESS_RESPONSE_HEADERS.contains(name) && !suppressConnection.contains(name)) {
                exchange.response.headers.add(name, header.getValue());
            }
        }
// TODO: decide if need to try-finally to call httpRequest.abort?
        timer.stop();
    }

    /** A request that encloses an entity. */
    private static class EntityRequest extends HttpEntityEnclosingRequestBase {
        private final String method;
        public EntityRequest(Request request) {
            this.method = request.method;
            InputStreamEntity entity = new InputStreamEntity(request.entity, new ContentLengthHeader(request).length);
            entity.setContentType(new ContentTypeHeader(request).toString());
            entity.setContentEncoding(new ContentEncodingHeader(request).toString());
            setEntity(entity);
        }
        @Override public String getMethod() {
            return method;
        }
    }

    /** A request that does not enclose an entity. */
    private static class NonEntityRequest extends HttpRequestBase {
        private final String method;
        public NonEntityRequest(Request request) {
            this.method = request.method;
            Header contentLengthHeader[] = getHeaders(ContentLengthHeader.NAME);
            if ((contentLengthHeader == null || contentLengthHeader.length == 0)
                    && ("PUT".equals(method) || "POST".equals(method) || "PROPFIND".equals(method))) {
                setHeader(ContentLengthHeader.NAME, "0");
            }
        }
        @Override public String getMethod() {
            return method;
        }
    }

    /** Creates and initializes a client handler in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override public Object create() throws HeapException, JsonValueException {
            Integer connections = config.get("connections").defaultTo(DEFAULT_CONNECTIONS).asInteger(); // optional, default to DEFAULT_CONNECTIONS number of connections
            // determines if connections should be reused, disables keep-alive
            Boolean disableReuseConnection = config.get("disableReuseConnection").defaultTo(false).asBoolean(); // optional, default false
            // determines if requests should be retried on failure
            Boolean disableRetries = config.get("disableRetries").defaultTo(false).asBoolean(); // optional, default false

            return new ClientHandler(connections.intValue(), this.storage,
                    disableReuseConnection.booleanValue(), disableRetries.booleanValue());
        }
    }
}
