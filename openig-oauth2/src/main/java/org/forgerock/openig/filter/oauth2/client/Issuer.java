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
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.getJsonContent;
import static org.forgerock.openig.heap.Keys.HTTP_CLIENT_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.evaluate;

import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.util.Reject;

/**
 * A configuration for an OpenID Connect Issuer. Two approaches to create the
 * Issuer:
 * <p>
 * With an OpenId well-known end-point:
 * </p>
 *
 * <pre>
 * {@code
 * {
 *   "wellKnownEndpoint"            : uriExpression,   [REQUIRED]
 *   "issuerHandler"                : handler          [OPTIONAL - default is using a new ClientHandler
 *                                                                 wrapping the default HttpClient.]
 * }
 * }
 * </pre>
 * For example, use this kind of configuration if the end-points are not known:
 * <pre>
 * {@code
 * {
 *     "name": "openam",
 *     "type": "Issuer",
 *     "config": {
 *          "wellKnownEndpoint": "http://www.example.com:8081/openam/oauth2/.well-known/openid-configuration"
 *     }
 * }
 * }
 * </pre>
 * <br>
 * <p>
 * If the end-points are known, even the well-known end-point.
 * </p>
 *
 * <pre>
 * {@code
 * {
 *   "authorizeEndpoint"            : uriExpression,   [REQUIRED]
 *   "tokenEndpoint"                : uriExpression,   [REQUIRED]
 *   "registrationEndpoint"         : uriExpression,   [OPTIONAL - allows dynamic client registration]
 *   "userInfoEndpoint"             : uriExpression    [OPTIONAL - default is no user info]
 *   "wellKnownEndpoint"            : uriExpression    [OPTIONAL]
 * }
 * }
 * </pre>
 *
 * For example:
 *
 * <pre>
 * {@code
 * {
 *     "name": "openam",
 *     "type": "Issuer",
 *     "config": {
 *          "authorizeEndpoint": "http://www.example.com:8081/openam/oauth2/authorize",
 *          "tokenEndpoint": "http://www.example.com:8081/openam/oauth2/access_token",
 *          "userInfoEndpoint": "http://www.example.com:8081/openam/oauth2/userinfo"
 *          "wellKnownEndpoint": "http://www.example.com:8081/openam/oauth2/.well-known/openid-configuration"
 *     }
 * }
 * }
 * </pre>
 */
public final class Issuer {
    /** The key used to store this issuer in the exchange. */
    public static final String ISSUER_KEY = "issuer";

    private final String name;
    private final URI authorizeEndpoint;
    private final URI tokenEndpoint;
    private final URI registrationEndpoint;
    private final URI userInfoEndpoint;
    private URI wellKnownEndpoint;

    /**
     * Creates an issuer with the specified name and configuration.
     *
     * @param name
     *            The name of this Issuer. When the issuer is created by
     *            discovery, the issuer name is given by the metadata "issuer",
     *            not null.
     * @param config
     *            The configuration of this issuer, not null.
     */
    public Issuer(final String name, final JsonValue config) {
        Reject.ifNull(name, config);
        this.name = name;
        this.authorizeEndpoint =
                config.get("authorizeEndpoint").defaultTo(config.get("authorization_endpoint")).required().asURI();
        this.tokenEndpoint = config.get("tokenEndpoint").defaultTo(config.get("token_endpoint")).required().asURI();
        this.registrationEndpoint =
                config.get("registrationEndpoint").defaultTo(config.get("registration_endpoint")).asURI();
        this.userInfoEndpoint = config.get("userInfoEndpoint").defaultTo(config.get("userinfo_endpoint")).asURI();
        this.wellKnownEndpoint = config.get("wellKnownEndpoint").asURI();
    }

    /**
     * Returns the name of this issuer.
     *
     * @return the name of this issuer.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the authorize end-point of this issuer.
     *
     * @return the authorize end-point of this issuer.
     */
    public URI getAuthorizeEndpoint() {
        return authorizeEndpoint;
    }

    /**
     * Returns the token end-point of this issuer.
     *
     * @return the token end-point of this issuer.
     */
    public URI getTokenEndpoint() {
        return tokenEndpoint;
    }

    /**
     * Returns the registration end-point of this issuer.
     *
     * @return the registration end-point of this issuer.
     */
    public URI getRegistrationEndpoint() {
        return registrationEndpoint;
    }

    /**
     * Returns the user end-point of this issuer.
     *
     * @return the user end-point of this issuer.
     */
    public URI getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    /**
     * Returns the well-known end-point of this issuer.
     *
     * @return the well-known end-point of this issuer.
     */
    public URI getWellKnownEndpoint() {
        return wellKnownEndpoint;
    }

    /**
     * Returns {@code true} if this issuer has a user info end-point.
     *
     * @return {@code true} if this issuer has a user info end-point.
     */
    public boolean hasUserInfoEndpoint() {
        return userInfoEndpoint != null;
    }

    /**
     * Builds a new Issuer based on the given well-known URI.
     *
     * @param name
     *            The issuer's identifier. Usually, it's the host name or a
     *            given name.
     * @param wellKnownUri
     *            The well-known URI of this issuer.
     * @param handler
     *            The issuer handler that does the call to the given well-known
     *            URI.
     * @return An OpenID issuer.
     * @throws DiscoveryException
     *             If an error occurred when retrieving the JSON content from
     *             the server response.
     */
    public static Issuer build(final String name,
                               final URI wellKnownUri,
                               final Handler handler) throws DiscoveryException {
        final Request request = new Request();
        request.setMethod("GET");
        request.setUri(wellKnownUri);

        final Response response = handler.handle(new Exchange(), request)
                                               .getOrThrowUninterruptibly();
        if (!OK.equals(response.getStatus())) {
            throw new DiscoveryException("Unable to read well-known OpenID Configuration from '"
                    + wellKnownUri + "'");
        }
        JsonValue config = null;
        try {
            config = getJsonContent(response);
        } catch (OAuth2ErrorException | JsonValueException e) {
            throw new DiscoveryException(e.getMessage());
        }
        return new Issuer(name, config);
    }

    /** Creates and initializes an Issuer object in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            Handler issuerHandler = null;
            if (config.isDefined("issuerHandler")) {
                issuerHandler = heap.resolve(config.get("issuerHandler"), Handler.class);
            } else {
                issuerHandler = new ClientHandler(heap.get(HTTP_CLIENT_HEAP_KEY, HttpClient.class));
            }

            if (config.isDefined("wellKnownEndpoint")
                    && !config.isDefined("authorizeEndpoint")
                    && !config.isDefined("tokenEndpoint")) {
                try {
                    final URI wellKnownEndpoint = new URI(evaluate(config.get("wellKnownEndpoint")));
                    return build(this.name, wellKnownEndpoint, issuerHandler);
                } catch (DiscoveryException | URISyntaxException e) {
                    throw new HeapException(e);
                }
            }
            return new Issuer(this.name, evaluate(config, logger));
        }
    }
}
