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

import static org.forgerock.http.protocol.Status.CREATED;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.ClientRegistration.CLIENT_REG_KEY;
import static org.forgerock.openig.filter.oauth2.client.Issuer.ISSUER_KEY;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.getJsonContent;
import static org.forgerock.openig.heap.Keys.HTTP_CLIENT_HEAP_KEY;
import static org.forgerock.openig.http.Responses.newInternalServerError;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.net.URI;

import org.forgerock.http.Context;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
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
 * The dynamic registration filter is the way to dynamically register an OpenID
 * Connect Relying Party with the End-User's OpenID Provider.
 * <p>
 * Options:
 * </p>
 *
 * <pre>
 * {@code
 * {
 *   "redirect_uris"                : [ expressions ],  [REQUIRED]
 *   "registrationHandler"          : handler           [OPTIONAL - default is using a new ClientHandler
 *                                                                 wrapping the default HttpClient.]
 * }
 * }
 * </pre>
 *
 * This configuration allows the user to add every attributes that are supported
 * by the specification, like application_type, logo_uri,
 * token_endpoint_auth_method, etc. needed to create this client registration.
 *
 * <pre>
 * {@code
 * {
 *   "type": "DynamicRegistrationFilter",
 *   "config": {
 *       "contacts": ["ve7jtb@example.org", "mary@example.org"],
 *       "redirect_uris": [
 *           "http://localhost:8082/openid/callback"
 *       ],
 *       "application_type": "web",
 *       "logo_uri": "https://client.example.org/logo.png",
 *       "scopes": [
 *           "openid", "profile"
 *       ],
 *       "token_endpoint_auth_method": "client_secret_basic"
 *   }
 * }
 * }
 * </pre>
 *
 * @see <a href="https://openid.net/specs/openid-connect-registration-1_0.html">
 *      OpenID Connect Dynamic Client Registration 1.0</a>
 */
public class ClientRegistrationFilter extends GenericHeapObject implements Filter {
    private final Handler registrationHandler;
    private final Heap heap;
    private final JsonValue config;

    /**
     * Creates a new dynamic registration filter.
     *
     * @param registrationHandler
     *            The handler to perform the dynamic registration to the AS.
     * @param config
     *            The configuration of this filter. Must contains the
     *            'redirect_uris' attributes.
     * @param heap A reference to the current heap.
     */
    public ClientRegistrationFilter(final Handler registrationHandler,
                                    final JsonValue config,
                                    final Heap heap) {
        this.registrationHandler = registrationHandler;
        this.config = config;
        this.heap = heap;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        try {
            final Exchange exchange = context.asContext(Exchange.class);
            final Issuer issuer = (Issuer) exchange.getAttributes().get(ISSUER_KEY);
            if (issuer != null && issuer.getRegistrationEndpoint() != null) {
                // TODO check from heap if the client registration exists.
                final JsonValue registeredClientConfiguration =
                        performDynamicClientRegistration(context, config, issuer.getRegistrationEndpoint());
                final ClientRegistration cr =
                        heap.resolve(createClientRegistrationDeclaration(registeredClientConfiguration,
                                                                         issuer.getName()), ClientRegistration.class);

                exchange.getAttributes().put(CLIENT_REG_KEY, cr);
            } else {
                throw new RegistrationException("Do not support dynamic client registration");
            }
        } catch (RegistrationException e) {
            return newResultPromise(
                    newInternalServerError("An error occured during dynamic registration process", e));
        } catch (HeapException e) {
            return newResultPromise(
                    newInternalServerError("Cannot inject inlined Client Registration declaration to heap", e));
        }
        return next.handle(context, request);
    }

    private JsonValue createClientRegistrationDeclaration(final JsonValue configuration, final String issuerName) {
        configuration.put("issuer", issuerName);
        return json(object(
                        // TODO then name of the client registration should be issuerName + OAuth2ClientFilter name.
                        field("name", configuration.get("client_name").defaultTo(config.get("client_id")).asString()),
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
            throw new RegistrationException("Cannot perform dynamic registration : this can be caused "
                                            + "by the distant server(busy, offline...) "
                                            + "or a malformed registration response.");
        }
        try {
            return getJsonContent(response);
        } catch (OAuth2ErrorException e) {
            throw new RegistrationException("Cannot perform dynamic registration : invalid response JSON content.");
        }
    }

    /** Creates and initializes the dynamic registration filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            Handler handler = null;
            if (config.isDefined("registrationHandler")) {
                handler = heap.resolve(config.get("registrationHandler"), Handler.class);
            } else {
                handler = new ClientHandler(heap.get(HTTP_CLIENT_HEAP_KEY, HttpClient.class));
            }
            config.get("redirectUris").defaultTo(config.get("redirect_uris")).required().asList(String.class);
            return new ClientRegistrationFilter(handler, config, heap);
        }
    }
}
