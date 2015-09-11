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

import static java.lang.String.format;
import static org.forgerock.http.protocol.Status.CREATED;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.ClientRegistration.CLIENT_REG_KEY;
import static org.forgerock.openig.filter.oauth2.client.Issuer.ISSUER_KEY;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.getJsonContent;
import static org.forgerock.openig.http.Responses.newInternalServerError;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.net.URI;

import org.forgerock.services.context.Context;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * The client registration filter is the way to dynamically register an OpenID
 * Connect Relying Party with the End-User's OpenID Provider.
 * <p>
 * All meta data must be included in the OAuth2ClientFilter configuration.
 * Dynamic client registration requires the "redirect_uris" attributes.
 * </p>
 *
 * <pre>
 * {@code
 * {
 *  "name": "Portal",
 *     "type": "OAuth2ClientFilter",
 *     "config": {
 *         "clientEndpoint": "/openid",
 *         "requireHttps": false,
 *         "requireLogin": true,
 *         "loginHandler": "NascarPage",
 *         "scopes": [
 *             "openid",
 *             "profile",
 *             "email"
 *         ],
 *         "target": "${exchange.attributes.openid}",
 *         "failureHandler": ...,
 *         "redirect_uris": [
 *             "http://localhost:8082/openid/callback",
 *             "http://localhost:8082/openid/callback2"
 *         ],
 *         "client_name": "My App",
 *         "contacts": ["ve7jtb@example.org", "mary@example.org"]
 * }
 * </pre>
 *
 * <br>
 * Note for developers: The suffix is added to the issuer name to compose the
 * client registration name in the current heap. When automatically called by
 * the OAuth2Client filter, this name is <IssuerName> + <OAuth2ClientFilterName>
 * This is required in order to retrieve the Client Registration when performing
 * dynamic client registration.
 *
 * @see <a href="https://openid.net/specs/openid-connect-registration-1_0.html">
 *      OpenID Connect Dynamic Client Registration 1.0</a>
 */
public class ClientRegistrationFilter implements Filter {
    private final Handler registrationHandler;
    private final Heap heap;
    private final JsonValue config;
    private final String suffix;

    /**
     * Creates a new dynamic registration filter.
     *
     * @param registrationHandler
     *            The handler to perform the dynamic registration to the AS.
     * @param config
     *            The configuration of this filter. Must contains the
     *            'redirect_uris' attributes.
     * @param heap
     *            A reference to the current heap.
     * @param suffix
     *            The name of the client registration in the heap will be
     *            <IssuerName> + <suffix>. Must not be {@code null}.
     */
    public ClientRegistrationFilter(final Handler registrationHandler,
                                    final JsonValue config,
                                    final Heap heap,
                                    final String suffix) {
        this.registrationHandler = registrationHandler;
        this.config = config;
        this.heap = heap;
        this.suffix = checkNotNull(suffix);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context,
                                                          Request request,
                                                          Handler next) {
        try {
            final Exchange exchange = context.asContext(Exchange.class);
            final Issuer issuer = (Issuer) exchange.getAttributes().get(ISSUER_KEY);
            if (issuer != null) {
                ClientRegistration cr = heap.get(issuer.getName() + suffix, ClientRegistration.class);
                if (cr == null) {
                    if (!config.isDefined("redirect_uris")) {
                        throw new RegistrationException(
                                "Cannot perform dynamic registration: 'redirect_uris' should be defined");
                    }
                    if (issuer.getRegistrationEndpoint() == null) {
                        throw new RegistrationException(format("Registration is not supported by the issuer '%s'",
                                issuer));
                    }
                    final JsonValue registeredClientConfiguration = performDynamicClientRegistration(context, config,
                            issuer.getRegistrationEndpoint());
                    cr = heap.resolve(
                            createClientRegistrationDeclaration(registeredClientConfiguration, issuer.getName()),
                            ClientRegistration.class);
                }
                exchange.getAttributes().put(CLIENT_REG_KEY, cr);
            } else {
                throw new RegistrationException("Cannot retrieve issuer from the exchange");
            }
        } catch (RegistrationException e) {
            return newResultPromise(newInternalServerError(e));
        } catch (HeapException e) {
            return newResultPromise(
                    newInternalServerError("Cannot inject inlined Client Registration declaration to heap", e));
        }
        return next.handle(context, request);
    }

    private JsonValue createClientRegistrationDeclaration(final JsonValue configuration, final String issuerName) {
        configuration.put("issuer", issuerName);
        return json(object(
                        field("name", issuerName + suffix),
                        field("type", "ClientRegistration"),
                        field("config", configuration)));
    }

    JsonValue performDynamicClientRegistration(final Context context,
                                               final JsonValue clientRegistrationConfiguration,
                                               final URI registrationEndpoint) throws RegistrationException {
        final Request request = new Request();
        request.setMethod("POST");
        request.setUri(registrationEndpoint);
        request.setEntity(clientRegistrationConfiguration.asMap());

        final Response response = registrationHandler.handle(context, request)
                                                     .getOrThrowUninterruptibly();
        if (!CREATED.equals(response.getStatus())) {
            throw new RegistrationException("Cannot perform dynamic registration: this can be caused "
                                            + "by the distant server(busy, offline...) "
                                            + "or a malformed registration response.");
        }
        try {
            return getJsonContent(response);
        } catch (OAuth2ErrorException e) {
            throw new RegistrationException("Cannot perform dynamic registration: invalid response JSON content.");
        }
    }
}
