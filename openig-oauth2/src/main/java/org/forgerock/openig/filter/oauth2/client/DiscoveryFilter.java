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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static java.lang.String.format;
import static org.forgerock.http.Responses.newInternalServerError;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.http.util.Uris.withQuery;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.Issuer.ISSUER_KEY;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.getJsonContent;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Pattern;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.Responses;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
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
 * The user input is given
 * from the query parameters {@code '?discovery=<userInput>'}.
 * <br>
 * Discovery is in two part. The first extracts the host name and a normalized
 * user input from the given input.
 * <br>
 * Then, IG verifies if an existing {@link Issuer} already exists in the heap
 * corresponding to the extracted host name. If it exists, reuse it. If not,
 * IG verifies this host name is not part of an Issuer "supportedDomain".
 * If the host name belongs to an {@link Issuer} supported Domain, this
 * {@link Issuer} is used. Otherwise, discovery process continues...
 * <br>
 * In the second part, the WebFinger uses the extracted host name,
 * to get the corresponding OpenID Issuer location which match the selected
 * type of service ("http://openid.net/specs/connect/1.0/issuer") if it exists.
 * <br>
 * Based on the returned OpenID Issuer's location, the OpenID well-known
 * end-point is extracted and the filter builds a {@link Issuer} which is
 * placed in the context and in the heap to be reused if needed.
 * </p>
 *
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">
 *      OpenID Connect Dynamic Client Registration 1.0</a>
 * @see <a href="https://tools.ietf.org/html/rfc7033">WebFinger</a>
 */
public class DiscoveryFilter implements Filter {
    static final String OPENID_SERVICE = "http://openid.net/specs/connect/1.0/issuer";
    private static final String WELLKNOWN_WEBFINGER = ".well-known/webfinger";
    private static final String WELLKNOWN_OPENID_CONFIGURATION = ".well-known/openid-configuration";

    private final Handler discoveryHandler;
    private final Heap heap;
    private final Logger logger;

    /**
     * Creates a discovery filter.
     * @param handler
     *            The handler to perform the queries.
     * @param heap
     *            A reference to the current heap.
     * @param logger
     *            For logging activities.
     */
    DiscoveryFilter(final Handler handler, final Heap heap, final Logger logger) {
        this.discoveryHandler = handler;
        this.heap = heap;
        this.logger = logger;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        return retrieveIssuer(context, request)
                .thenAsync(new AsyncFunction<Issuer, Response, NeverThrowsException>() {
                    @Override
                    public Promise<Response, NeverThrowsException> apply(Issuer issuer) {
                        AttributesContext attributesContext = context.asContext(AttributesContext.class);
                        attributesContext.getAttributes().put(ISSUER_KEY, issuer);

                        return next.handle(context, request);
                    }
                }, new AsyncFunction<DiscoveryException, Response, NeverThrowsException>() {
                    @Override
                    public Promise<Response, NeverThrowsException> apply(DiscoveryException e) {
                        logger.error(e);
                        return newResultPromise(newInternalServerError(e));
                    }
                });
    }

    private Promise<Issuer, DiscoveryException> retrieveIssuer(Context context, Request request) {
        final AccountIdentifier account;
        try {
            account = extractFromInput(request.getForm().getFirst("discovery"));
        } catch (DiscoveryException e) {
            return newExceptionPromise(e);
        }

        final String hostString = account.getHostBase().toASCIIString();
        Issuer issuer;
        try {
            // Auto-created Issuer heap objects are named according to the discovered host base.
            issuer = heap.get(hostString, Issuer.class);
            if (issuer != null) {
                return newResultPromise(issuer);
            }

            // Checks if this domain name should be supported by an existing issuer.
            issuer = fromSupportedDomainNames(hostString);
            if (issuer != null) {
                return newResultPromise(issuer);
            }
        } catch (HeapException e) {
            return newExceptionPromise(new DiscoveryException("Error while retrieving the Issuer", e));
        }

        // Performs discovery otherwise.
        return performOpenIdIssuerDiscovery(context, account)
                .then(new Function<URI, Issuer, DiscoveryException>() {
                    @Override
                    public Issuer apply(URI wellKnownUri) throws DiscoveryException {
                        JsonValue issuerDeclaration = createIssuerDeclaration(hostString, wellKnownUri);
                        try {
                            return heap.resolve(issuerDeclaration, Issuer.class);
                        } catch (HeapException e) {
                            String message = format("Cannot resolve the issuerDeclaration '%s'",
                                                    issuerDeclaration.toString());
                            logger.error(message);
                            logger.error(e);
                            throw new DiscoveryException(message, e);
                        }
                    }
                });
    }

    /**
     * The given domain name can match one or none domain names supported by
     * Issuers declared in this route. If the given domain name matches the
     * patterns given by an Issuer 'supportedDomains' attributes, then the
     * corresponding Issuer is returned to be used.
     */
    private Issuer fromSupportedDomainNames(final String givenDomainName) throws HeapException {
        for (final Issuer definedIssuer : heap.getAll(Issuer.class)) {
            final List<Pattern> domainNames = definedIssuer.getSupportedDomains();
            for (final Pattern domainName : domainNames) {
                if (domainName.matcher(givenDomainName).matches()) {
                    return definedIssuer;
                }
            }
        }
        return null;
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
     * @return A promise completed with the '.well-known' URI if succeed or with a {@link DiscoveryException} if not.
     */
    Promise<URI, DiscoveryException> performOpenIdIssuerDiscovery(final Context context,
                                                                  final AccountIdentifier account) {
        return discoveryHandler.handle(context, buildWebFingerRequest(account))
                               .then(extractWellKnownUri(), Responses.<URI, DiscoveryException>noopExceptionFunction());

    }

    private Function<Response, URI, DiscoveryException> extractWellKnownUri() {
        return new Function<Response, URI, DiscoveryException>() {
            @Override
            public URI apply(Response response) throws DiscoveryException {
                if (!OK.equals(response.getStatus())) {
                    throw new DiscoveryException(format("Invalid response received from WebFinger URI : expected OK "
                                                                + "but got %s", response.getStatus()));
                }
                return buildUri(extractWebFingerHref(response)).resolve(WELLKNOWN_OPENID_CONFIGURATION);
            }

            private String extractWebFingerHref(Response response) throws DiscoveryException {
                String webFingerHref = null;
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
                    throw new DiscoveryException("Invalid JSON response", e);
                } catch (OAuth2ErrorException e) {
                    throw new DiscoveryException("Cannot read JSON response in webfinger process", e);
                }

                if (webFingerHref == null) {
                    throw new DiscoveryException("Invalid WebFinger URI : this can be caused by the distant server or "
                                                         + "a malformed WebFinger file");
                }
                return webFingerHref.endsWith("/") ? webFingerHref : webFingerHref + "/";
            }

            private URI buildUri(String uri) throws DiscoveryException {
                try {
                    return new URI(uri);
                } catch (URISyntaxException e) {
                    throw new DiscoveryException(format("Invalid URI '%s'", uri));
                }
            }
        };
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
    static AccountIdentifier extractFromInput(final String decodedUserInput) throws DiscoveryException {
        if (decodedUserInput == null || decodedUserInput.isEmpty()) {
            throw new DiscoveryException("Invalid input");
        }

        try {
            final URI uri;
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
        } catch (URISyntaxException e) {
            throw new DiscoveryException("Unable to parse the user input", e);
        }
    }

    static final class AccountIdentifier {
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
         * Returns the host base of this account which could belong to an
         * {@link Issuer} supported domains or should be the host location where
         * is hosted the WebFinger service.
         *
         * @return The host base of this account.
         */
        URI getHostBase() {
            return hostBase;
        }
    }
}
