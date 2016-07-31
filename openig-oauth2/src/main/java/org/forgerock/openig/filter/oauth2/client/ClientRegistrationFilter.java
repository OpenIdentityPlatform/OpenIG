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
import static org.forgerock.http.protocol.Responses.internalServerError;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.http.protocol.Status.CREATED;
import static org.forgerock.openig.filter.oauth2.client.ClientRegistration.CLIENT_REG_KEY;
import static org.forgerock.openig.filter.oauth2.client.Issuer.ISSUER_KEY;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.getJsonContent;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.net.URI;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Responses;
import org.forgerock.json.JsonValue;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client registration filter is the way to dynamically register an OpenID
 * Connect Relying Party with the End-User's OpenID Provider.
 * <p>
 * All OpenID metadata must be included in the <b>{@link OAuth2ClientFilter}</b> configuration,
 * in the <b>"metadata" attribute</b>. Note that for dynamic client registration,
 * only the "redirect_uris" attribute is mandatory.
 * </p>
 *
 * Note: When using OpenAM, the "scopes" may be specified to this configuration but
 * it must be defined as: "scopes"(array of string), which differs from
 * the OAuth2 metadata "scope" (a string containing a space separated list of scope values).
 *
 * @see <a href="https://openid.net/specs/openid-connect-registration-1_0.html">
 *      OpenID Connect Dynamic Client Registration 1.0</a>
 * @see <a
 *      href="https://openid.net/specs/openid-connect-registration-1_0.html#ClientMetadata">
 *      OpenID Connect Dynamic Client Registration 1.0 </a>
 * @see <a
 *      href="https://tools.ietf.org/html/draft-ietf-oauth-dyn-reg-30#section-2">
 *      OAuth 2.0 Dynamic Client Registration Protocol </a>
 */
public class ClientRegistrationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ClientRegistrationFilter.class);

    private final ClientRegistrationRepository registrations;
    private final Handler registrationHandler;
    private final JsonValue config;

    /**
     * Creates a new dynamic registration filter.
     * @param repository
     *            The {@link ClientRegistrationRepository} holding the
     *            registrations values.
     * @param registrationHandler
     *            The handler to perform the dynamic registration to the
     *            Authorization Server(AS).
     * @param config
     *            Can contain any client metadata attributes that the client
     *            chooses to specify for itself during the registration. Must
     *            contains the 'redirect_uris' attributes.
     */
    public ClientRegistrationFilter(final ClientRegistrationRepository repository,
                                    final Handler registrationHandler,
                                    final JsonValue config) {
        this.registrations = checkNotNull(repository);
        this.registrationHandler = registrationHandler;
        this.config = config;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        final Map<String, Object> attributes = context.asContext(AttributesContext.class).getAttributes();
        final Issuer issuer = (Issuer) attributes.get(ISSUER_KEY);
        if (issuer == null) {
            final String message = "Cannot retrieve issuer from the context";
            logger.error(message);
            return newResultPromise(newInternalServerError(new RegistrationException(message)));
        }

        return retrieveClientRegistration(context, issuer)
                .thenAsync(filterWithClientRegistration(context, request, next, attributes),
                           internalServerError());
    }

    private AsyncFunction<ClientRegistration, Response, NeverThrowsException> filterWithClientRegistration(
            final Context context,
            final Request request,
            final Handler next,
            final Map<String, Object> attributes) {
        return new AsyncFunction<ClientRegistration, Response, NeverThrowsException>() {
            @Override
            public Promise<Response, NeverThrowsException> apply(ClientRegistration clientRegistration) {
                attributes.put(CLIENT_REG_KEY, clientRegistration);
                return next.handle(context, request);
            }
        };
    }

    private Promise<ClientRegistration, RegistrationException> retrieveClientRegistration(final Context context,
                                                                                          final Issuer issuer) {
        ClientRegistration cr = registrations.findByIssuer(issuer);
        if (cr != null) {
            return newResultPromise(cr);
        }

        // No matching ClientRegistration found, let's see if we can build one
        if (!config.isDefined("redirect_uris")) {
            final String message = "Cannot perform dynamic registration: 'redirect_uris' should be defined";
            logger.error(message);
            return newExceptionPromise(new RegistrationException(message));
        }
        if (issuer.getRegistrationEndpoint() == null) {
            final String message = format("Registration is not supported by the issuer '%s'", issuer.getName());
            logger.error(message);
            return newExceptionPromise(new RegistrationException(message));
        }

        return performDynamicClientRegistration(context, config, issuer.getRegistrationEndpoint())
                .then(new Function<JsonValue, ClientRegistration, RegistrationException>() {
                    @Override
                    public ClientRegistration apply(JsonValue registeredClientConfiguration)
                            throws RegistrationException {
                        final ClientRegistration registration = new ClientRegistration(null,
                                                                                       registeredClientConfiguration,
                                                                                       issuer,
                                                                                       registrationHandler);
                        registrations.add(registration);
                        return registration;
                    }
                });
    }

    Promise<JsonValue, RegistrationException> performDynamicClientRegistration(
            final Context context,
            final JsonValue clientRegistrationConfiguration,
            final URI registrationEndpoint) {
        final Request request = new Request();
        request.setMethod("POST");
        request.setUri(registrationEndpoint);
        request.setEntity(clientRegistrationConfiguration.asMap());

        return registrationHandler.handle(context, request)
                                  .then(extractJsonContent(),
                                        Responses.<JsonValue, RegistrationException>noopExceptionFunction());
    }

    private Function<Response, JsonValue, RegistrationException> extractJsonContent() {
        return new Function<Response, JsonValue, RegistrationException>() {
            @Override
            public JsonValue apply(Response response) throws RegistrationException {
                if (!CREATED.equals(response.getStatus())) {
                    throw new RegistrationException("Cannot perform dynamic registration: this can be "
                                                            + "caused by the distant server(busy, offline...) "
                                                            + "or a malformed registration response.",
                                                    response.getCause());
                }
                try {
                    return getJsonContent(response);
                } catch (OAuth2ErrorException e) {
                    throw new RegistrationException("Cannot perform dynamic registration: invalid response "
                                                            + "JSON content.", e);
                }
            }
        };
    }
}
