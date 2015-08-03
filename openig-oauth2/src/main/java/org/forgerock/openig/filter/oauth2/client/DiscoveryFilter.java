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
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.http.util.Uris.urlDecode;
import static org.forgerock.http.util.Uris.withQuery;
import static org.forgerock.json.fluent.JsonValue.field;
import static org.forgerock.json.fluent.JsonValue.json;
import static org.forgerock.json.fluent.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.Issuer.ISSUER_KEY;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.getJsonContent;
import static org.forgerock.openig.heap.Keys.HTTP_CLIENT_HEAP_KEY;
import static org.forgerock.openig.http.Responses.newInternalServerError;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.forgerock.http.Context;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * In order for an OpenID Connect Relying Party to utilize OpenID Connect
 * services for an End-User, the RP needs to know where the OpenID Provider is.
 * OpenID Connect uses WebFinger [RFC7033] to locate the OpenID Provider for an
 * End-User.
 * <p>
 * This class performs OpenID Provider Issuer discovery : determine the location
 * of the OpenID Provider based on a given End-User input which can be an e-mail
 * address or a URL Syntax or even a HostName and Port Syntax.
 * </p>
 * <p>
 * The user input is given from the query parameters {@code '?discovery=<userInput>'}.
 * <br>
 * Several info are extracted from the input to retrieve the OpenID Issuer.
 * <br>
 * The WebFinger requests the extracted hostname, to get the corresponding
 * OpenID Issuer location which match the selected type of
 * service ("http://openid.net/specs/connect/1.0/issuer").
 * <br>
 * Based on the Issuer location, the OpenID well-known end-point is extracted and
 * the filter builds a {@link Issuer} which is placed in the exchange and in the heap.
 * </p>
 *
 * <pre>
 * {@code
 * {
 *   "discoveryHandler" : handler          [OPTIONAL - default is using a new ClientHandler
 *                                                     wrapping the default HttpClient.]
 * }
 * }
 * </pre>
 *
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">
 *      OpenID Connect Dynamic Client Registration 1.0</a>
 * @see <a href="https://tools.ietf.org/html/rfc7033">WebFinger</a>
 */
public class DiscoveryFilter extends GenericHeapObject implements Filter {
    static final String OPENID_SERVICE = "http://openid.net/specs/connect/1.0/issuer";
    private static final String WELLKNOWN_WEBFINGER = ".well-known/webfinger";
    private static final String WELLKNOWN_OPENID_CONFIGURATION = ".well-known/openid-configuration";

    private final Handler discoveryHandler;
    private final Heap heap;

    /**
     * Creates a discovery filter.
     *
     * @param handler
     *            The handler to perform the queries.
     * @param heap
     *            A reference to the current heap.
     */
    DiscoveryFilter(final Handler handler, final Heap heap) {
        this.discoveryHandler = handler;
        this.heap = heap;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context,
                                                          Request request,
                                                          Handler next) {
        final Exchange exchange = context.asContext(Exchange.class);

        try {
            final AccountIdentifier account = extractFromInput(urlDecode(request.getForm().getFirst("discovery")));

            Issuer issuer = heap.get(account.getHostBase().toString(), Issuer.class);
            if (issuer == null) {
                final URI wellKnowIssuerUri = performOpenIdIssuerDiscovery(context, account);
                final JsonValue issuerDeclaration = createIssuerDeclaration(account.getHostBase().toString(),
                                                                            wellKnowIssuerUri);
                issuer = heap.resolve(issuerDeclaration, Issuer.class);
            }
            exchange.put(ISSUER_KEY, issuer);
        } catch (URISyntaxException | DiscoveryException e) {
            return newResultPromise(newInternalServerError("Discovery cannot be performed", e));
        } catch (HeapException e) {
            return newResultPromise(newInternalServerError("Cannot inject inlined Issuer declaration to heap", e));
        }

        return next.handle(context, request);
    }

    private JsonValue createIssuerDeclaration(final String issuerName, final URI wellKnowIssuerUri) {
        return json(
                object(field("name", issuerName),
                       field("type", "Issuer"),
                       field("config", object(field("wellKnownEndpoint", wellKnowIssuerUri.toString())))));
    }

    /**
     * Performs the OpenID issuer discovery on the given URI.
     *
     * @param context
     *            Current context.
     * @param account
     *            The account identifier links to this input.
     * @return The '.well-known' URI if succeed.
     * @throws DiscoveryException
     *             If an error occurs during retrieving the WebFinger URI.
     * @throws URISyntaxException
     *             If an error occurs during building the WebFinger URI.
     */
    URI performOpenIdIssuerDiscovery(final Context context,
                                     final AccountIdentifier account) throws DiscoveryException,
                                                                             URISyntaxException {
        final Request request = buildWebFingerRequest(account);
        String webFingerHref = null;

        final Response response = discoveryHandler.handle(context, request)
                                                  .getOrThrowUninterruptibly();

        try {
            final JsonValue config = getJsonContent(response);
            final JsonValue links = config.get("links").expect(List.class);
            for (final JsonValue link : links) {
                if (OPENID_SERVICE.equals(link.get("rel").asString())) {
                    webFingerHref = link.get("href").asString();
                    break;
                }
            }
        } catch (JsonValueException e) {
            throw new DiscoveryException("Invalid Json response", e);
        } catch (OAuth2ErrorException e) {
            throw new DiscoveryException("Cannot read JSON response in webfinger process", e);
        }

        if (!OK.equals(response.getStatus()) || webFingerHref == null) {
            throw new DiscoveryException("Invalid WebFinger URI : this can be caused by the distant server or "
                                         + "a malformed WebFinger file");
        }
        final URI resourceLocation = new URI(webFingerHref.endsWith("/") ? webFingerHref : webFingerHref + "/");
        return resourceLocation.resolve(WELLKNOWN_OPENID_CONFIGURATION);
    }

    Request buildWebFingerRequest(final AccountIdentifier account) {
        final Request request = new Request();
        request.setMethod("GET");

        final Form query = new Form();
        query.add("resource", account.getNormalizedIdentifier().toString());
        query.add("rel", OPENID_SERVICE);
        final URI wellKnownWebFinger = withQuery(account.getHostBase().resolve(WELLKNOWN_WEBFINGER), query);
        request.setUri(wellKnownWebFinger);
        return request;
    }

    /**
     * Extracts from the given input the corresponding account identifier.
     *
     * @see <a
     *      href="https://openid.net/specs/openid-connect-discovery-1_0.html#NormalizationSteps">
     *      OpenID Connect Dynamic Client Registration 1.0 - Normalization
     *      steps</a>
     */
    AccountIdentifier extractFromInput(final String decodedUserInput) throws URISyntaxException {
        if (decodedUserInput == null || decodedUserInput.isEmpty()) {
            throw new IllegalArgumentException("Invalid input");
        }

        URI uri = null;
        String normalizedIdentifier = decodedUserInput;
        if (decodedUserInput.startsWith("acct:") || decodedUserInput.contains("@")) {
            /* email case */
            if (!decodedUserInput.startsWith("acct:")) {
                normalizedIdentifier = "acct:" + decodedUserInput;
            }
            if (decodedUserInput.lastIndexOf("@") > decodedUserInput.indexOf("@") + 1) {
                /* Extracting the host only when the input like 'joe@example.com@example.org' */
                /* the https scheme is assumed */
                uri = new URI("https://".concat(decodedUserInput.substring(decodedUserInput.lastIndexOf("@") + 1,
                                                                           decodedUserInput.length())));
            } else {
                uri = new URI(normalizedIdentifier.replace("acct:", "acct://"));
            }
        } else {
            uri = new URI(decodedUserInput);
            // Removing fragments
            if (decodedUserInput.contains("#")) {
                normalizedIdentifier = decodedUserInput.substring(0, decodedUserInput.indexOf("#"));
            }
        }

        final int port = uri.getPort();
        final String host = uri.getHost();
        return new AccountIdentifier(new URI(normalizedIdentifier),
                                     new URI("http".equals(uri.getScheme()) ? "http" : "https",
                                             null, host, port, "/", null, null));
    }

    /** Creates and initializes the discovery filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            Handler discoveryHandler = null;
            if (config.isDefined("discoveryHandler")) {
                discoveryHandler = heap.resolve(config.get("discoveryHandler"), Handler.class);
            } else {
                discoveryHandler = new ClientHandler(heap.get(HTTP_CLIENT_HEAP_KEY, HttpClient.class));
            }
            return new DiscoveryFilter(discoveryHandler, heap);
        }
    }

    final class AccountIdentifier {
        private final URI normalizedIdentifier;
        private final URI hostBase;

        AccountIdentifier(final URI normalizedIdentifier, final URI hostBase) {
            this.normalizedIdentifier = normalizedIdentifier;
            this.hostBase = hostBase;
        }

        /**
         * Returns the normalized identifier(defines the subject of the
         * requested resource for the WebFinger service).
         *
         * @return The normalized identifier.
         */
        URI getNormalizedIdentifier() {
            return normalizedIdentifier;
        }

        /**
         * Returns the host base of this account where a WebFinger service is
         * hosted.
         *
         * @return The host base of this account where a WebFinger service is
         *         hosted.
         */
        URI getHostBase() {
            return hostBase;
        }
    }
}
