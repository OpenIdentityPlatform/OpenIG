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
import static org.forgerock.http.Responses.internalServerError;
import static org.forgerock.http.Responses.newInternalServerError;
import static org.forgerock.http.protocol.Status.CREATED;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
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
import org.forgerock.http.Responses;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
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
 * <br>
 * Note for developers: The suffix is added to the issuer name to compose the
 * client registration name in the current heap. When automatically called by
 * the {@link OAuth2ClientFilter}, this name is {@literal IssuerName} + {@literal OAuth2ClientFilterName}
 * This is required in order to retrieve the Client Registration when performing
 * dynamic client registration.
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
    private final Handler registrationHandler;
    private final Heap heap;
    private final JsonValue config;
    private final String suffix;
    private final Logger logger;

    /**
     * Creates a new dynamic registration filter.
     *
     * @param registrationHandler
     *            The handler to perform the dynamic registration to the AS.
     * @param config
     *             Can contain any client metadata attributes that the client
     *             chooses to specify for itself during the registration. Must
     *             contains the 'redirect_uris' attributes.
     * @param heap
     *             A reference to the current heap.
     * @param suffix
     *             The name of the client registration in the heap will be
     *             {@literal IssuerName} + {@literal suffix}. Must not be {@code null}.
     * @param logger
     *             For logging activities.
     */
    public ClientRegistrationFilter(final Handler registrationHandler,
                                    final JsonValue config,
                                    final Heap heap,
                                    final String suffix,
                                    final Logger logger) {
        this.registrationHandler = registrationHandler;
        this.config = config;
        this.heap = heap;
        this.suffix = checkNotNull(suffix);
        this.logger = logger;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        final Map<String, Object> attributes = context.asContext(AttributesContext.class).getAttributes();
        final Issuer issuer = (Issuer) attributes.get(ISSUER_KEY);
        if (issuer == null) {
            return newResultPromise(newInternalServerError(
                    new RegistrationException("Cannot retrieve issuer from the context")));
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
        try {
            final ClientRegistration cr = heap.get(issuer.getName() + suffix, ClientRegistration.class);
            if (cr != null) {
                return newResultPromise(cr);
            }
        } catch (HeapException e) {
            String message = format("Error while trying to get the issuer '%s' from the heap", issuer.getName());
            logger.error(message);
            logger.error(e);
            return newExceptionPromise(new RegistrationException(message, e));
        }

        // No matching ClientRegistration found in the heap, let's see if we can build one
        if (!config.isDefined("redirect_uris")) {
            return newExceptionPromise(new RegistrationException(
                    "Cannot perform dynamic registration: 'redirect_uris' should be defined"));
        }
        if (issuer.getRegistrationEndpoint() == null) {
            return newExceptionPromise(
                    new RegistrationException(format("Registration is not supported by the issuer '%s'",
                                                     issuer.getName())));
        }

        return performDynamicClientRegistration(context, config, issuer.getRegistrationEndpoint())
                .then(new Function<JsonValue, ClientRegistration, RegistrationException>() {
                    @Override
                    public ClientRegistration apply(JsonValue registeredClientConfiguration)
                            throws RegistrationException {
                        try {
                            return heap.resolve(createClientRegistrationDeclaration(registeredClientConfiguration,
                                                                                    issuer.getName()),
                                                ClientRegistration.class);
                        } catch (HeapException e) {
                            String message = "Cannot inject inlined Client Registration declaration to heap";
                            logger.error(message);
                            logger.error(e);
                            throw new RegistrationException(message, e);
                        }
                    }
                });
    }

    private JsonValue createClientRegistrationDeclaration(final JsonValue configuration, final String issuerName) {
        configuration.put("issuer", issuerName);
        return json(object(
                       field("name", issuerName + suffix),
                       field("type", "ClientRegistration"),
                       field("config", configuration)));
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
