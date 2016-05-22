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
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.json.JsonValueFunctions.uri;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.getJsonContent;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.firstOf;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Responses;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.Function;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.Promise;

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
 *   "issuerHandler"                : handler          [OPTIONAL - by default it uses the 'ClientHandler'
 *                                                                 provided in heap.]
 *   "supportedDomains"             : [ patterns ]     [OPTIONAL - if this issuer supports other domain names]
 * }
 * }
 * </pre>
 *
 * The 'supportedDomains' are the other domain names supported by this issuer,
 * their format can include use of regular-expression patterns.
 * Nota: Declaring these domains in the configuration should be as simple as
 * possible, without any schemes or end slash i.e.:
 *
 * <pre>{@code
 * GOOD: [ "openam.com", "openam.com:8092", "register.server.com", "allopenamdomains.*" ]
 * BAD : [ "http://openam.com", "openam.com:8092/", "http://openam.com/" ]
 * }
 * </pre>
 *
 * <p>For example, use this kind of configuration if the end-points are not known:
 *
 * <pre>
 * {@code
 * {
 *     "name": "openam",
 *     "type": "Issuer",
 *     "config": {
 *          "wellKnownEndpoint": "http://www.example.com:8081/openam/oauth2/.well-known/openid-configuration"
 *          "supportedDomains" : [ "openam.com", "openam.com:8092", "register.server.com" ]
 *     }
 * }
 * }
 * </pre>
 *
 * <br>
 * <p>
 * Use this configuration if the end-points are known. The well-known end-point
 * is optional as the value will be saved but no request will be performed on
 * this end-point.
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
 *   "supportedDomains"             : [ patterns ]     [OPTIONAL - if this issuer supports other domain names]
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
 *     }
 * }
 * }
 * </pre>
 */
public final class Issuer {
    /** The key used to store this issuer in the context. */
    public static final String ISSUER_KEY = "issuer";

    private final String name;
    private final URI authorizeEndpoint;
    private final URI tokenEndpoint;
    private final URI registrationEndpoint;
    private final URI userInfoEndpoint;
    private URI wellKnownEndpoint;
    private final List<Pattern> supportedDomains;

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
        this.authorizeEndpoint = firstOf(config, "authorizeEndpoint", "authorization_endpoint").required().as(uri());
        this.tokenEndpoint = firstOf(config, "tokenEndpoint", "token_endpoint").required().as(uri());
        this.registrationEndpoint = firstOf(config, "registrationEndpoint", "registration_endpoint").as(uri());
        this.userInfoEndpoint = firstOf(config, "userInfoEndpoint", "userinfo_endpoint").as(uri());
        this.wellKnownEndpoint = config.get("wellKnownEndpoint").as(uri());
        this.supportedDomains = extractPatterns(config.get("supportedDomains").expect(List.class).asList(String.class));
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
     * Returns the unmodifiable list of the supported domain names.
     *
     * @return A unmodifiable list of the supported domain names.
     */
    public List<Pattern> getSupportedDomains() {
        return Collections.unmodifiableList(supportedDomains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.authorizeEndpoint, this.name, this.registrationEndpoint, this.supportedDomains,
                this.tokenEndpoint, this.userInfoEndpoint, this.wellKnownEndpoint);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Issuer other = (Issuer) obj;
        return Objects.equals(this.authorizeEndpoint, other.authorizeEndpoint)
                && Objects.equals(this.name, other.name)
                && Objects.equals(this.registrationEndpoint, other.registrationEndpoint)
                && Objects.equals(this.supportedDomains, other.supportedDomains)
                && Objects.equals(this.tokenEndpoint, other.tokenEndpoint)
                && Objects.equals(this.userInfoEndpoint, other.userInfoEndpoint)
                && Objects.equals(this.wellKnownEndpoint, other.wellKnownEndpoint);
    }

    /**
     * Builds a new Issuer based on the given well-known URI.
     *
     * @param context
     *            The context's chain.
     * @param name
     *            The issuer's identifier. Usually, it's the host name or a
     *            given name.
     * @param wellKnownUri
     *            The well-known URI of this issuer.
     * @param supportedDomains
     *            List of the supported domains for this issuer.
     * @param handler
     *            The issuer handler that does the call to the given well-known
     *            URI.
     * @return A promise completed with either an OAuth 2.0 issuer on success or a {@link DiscoveryException} on failure
     */
    public static Promise<Issuer, DiscoveryException> build(final Context context,
                                                            final String name,
                                                            final URI wellKnownUri,
                                                            final List<String> supportedDomains,
                                                            final Handler handler) {
        final Request request = new Request();
        request.setMethod("GET");
        request.setUri(wellKnownUri);

        return handler.handle(new AttributesContext(context), request)
                      .then(new Function<Response, Issuer, DiscoveryException>() {
                          @Override
                          public Issuer apply(Response response) throws DiscoveryException {
                              if (!OK.equals(response.getStatus())) {
                                  throw new DiscoveryException("Unable to read well-known OpenID Configuration from '"
                                                                       + wellKnownUri + "'", response.getCause());
                              }
                              try {
                                  JsonValue config = getJsonContent(response);
                                  return new Issuer(name, config.put("supportedDomains", supportedDomains));
                              } catch (OAuth2ErrorException | JsonValueException e) {
                                  throw new DiscoveryException("Cannot read JSON", e);
                              }
                          }
                      }, Responses.<Issuer, DiscoveryException>noopExceptionFunction());
    }

    private static List<Pattern> extractPatterns(final List<String> from) {
        final List<Pattern> patterns = new LinkedList<>();
        if (from != null) {
            for (String s : from) {
                try {
                    s = new StringBuilder("(http|https)://").append(s).append("/$").toString();
                    patterns.add(Pattern.compile(s));
                } catch (final PatternSyntaxException ex) {
                    // Ignore
                }
            }
        }
        return patterns;
    }

    /** Creates and initializes an Issuer object in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            final Handler issuerHandler = config.get("issuerHandler")
                                                .defaultTo(CLIENT_HANDLER_HEAP_KEY)
                                                .as(requiredHeapObject(heap, Handler.class));
            final List<String> supportedDomains = config.get("supportedDomains").asList(String.class);
            if (config.isDefined("wellKnownEndpoint")
                    && !config.isDefined("authorizeEndpoint")
                    && !config.isDefined("tokenEndpoint")) {
                try {
                    final URI wellKnownEndpoint = config.get("wellKnownEndpoint").as(evaluated()).as(uri());
                    return build(new AttributesContext(new RootContext()),
                                 this.name,
                                 wellKnownEndpoint,
                                 supportedDomains,
                                 issuerHandler).getOrThrow();
                } catch (DiscoveryException | InterruptedException e) {
                    throw new HeapException(format("Cannot build Issuer '%s'", name), e);
                }
            }
            return new Issuer(this.name, config.as(evaluated()));
        }
    }
}
