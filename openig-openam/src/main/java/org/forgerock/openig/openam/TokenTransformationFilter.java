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
 * Copyright 2018 3A Systems, LLC
 */

package org.forgerock.openig.openam;

import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.JsonValueFunctions.uri;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.heap.Keys.FORGEROCK_CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.forgerock.openig.util.JsonValues.slashEnded;
import static org.forgerock.util.Reject.checkNotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * A {@link TokenTransformationFilter} is responsible to transform a token issued by OpenAM
 * into a token of another type.
 *
  * <pre>
 *     {@code {
 *         "type": "TokenTransformationFilter",
 *         "config": {
 *             "openamUri": "https://openam.example.com/openam/",
 *             "realm": "/my-realm",
 *             "username": "${attributes.username}",
 *             "password": "${attributes.password}",
 *             "idToken": "${attributes.id_token}",
 *             "from": "OPENIDCONNECT",
 *             "to": "SAML2",
 *             "instance": "oidc-to-saml",
 *             "amHandler": "#Handler",
 *             "cache-size": "${32000}",
 *             "cache-ttl": "${0}",
 *         }
 *     }
 *     }
 * </pre>
 *
 * <p>The {@literal openamUri} attribute is the OpenAM base URI against which authentication
 * and STS requests will be issued.
 *
 * <p>The {@literal realm} attribute is the OpenAM realm that contains both the subject
 * (described through {@literal username} and {@literal password} attributes) and the STS
 * instance (described with {@literal instance}).
 *
 * <p>The {@literal idToken} attribute is an {@link Expression} specifying where to get the JWT id_token.
 * Note that the referenced value has to be a {@code String} (the JWT encoded value).
 *
 * <p>The {@literal instance} attribute is the name of an STS instance: a pre-configured transformation available
 * under a specific REST endpoint.
 *
 * <p>The {@literal amHandler} attribute is a reference to a {@link Handler} heap object. That handler will be used
 * for all REST calls to OpenAM (as opposed to the {@code next} Handler of the filter method that is dedicated to
 * continue the execution flow through the chain).
 *
 * <p>After transformation, the returned {@literal issued_token} (at the moment it is a {@code String} that contains
 * the XML of the generated SAML assertions), is made available in the {@link StsContext} for downstream handlers.
 *
 * <p>The {@literal cache-size} attribute is an {@link Expression} specifying cache size,  default value 32000 
 *
 * <p>The {@literal cache-ttl} attribute is an {@link Expression} specifying cache ttl in ms, default value 0 ms (cache disabled)
 *
 * <p>If errors are happening during the token transformation, the error response is returned as-is to the caller,
 * and informative messages are being logged for the administrator.
 */
public class TokenTransformationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(TokenTransformationFilter.class);

    private final Handler handler;
    private final URI endpoint;
    private final Expression<String> idToken;
    private final String from;
    private final String to;
    private final Cache<String,String> cache;
    /**
     * Constructs a new TokenTransformationFilter transforming the OpenID Connect id_token from {@code idToken}
     * into a SAML 2.0 Assertions structure (into {@code target}).
     *
     * @param handler pipeline used to send the STS transformation request
     * @param endpoint Fully qualified URI of the STS instance (including the {@literal _action=translate} query string)
     * @param idToken Expression for reading OpenID Connect id_token (expects a {@code String})
     */
    public TokenTransformationFilter(final Handler handler,
                                     final URI endpoint,
                                     final Expression<String> idToken,
                                     final String from,
                                     final String to,
                                     final Cache<String,String> cache) {
        this.handler = checkNotNull(handler);
        this.endpoint = checkNotNull(endpoint);
        this.idToken = checkNotNull(idToken);
        this.from = checkNotNull(from);
        this.to = checkNotNull(to);
        this.cache = cache;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {

    	String resolvedIdToken = idToken.eval(bindings(context, request));
        if (resolvedIdToken == null) {
            logger.debug("OpenID Connect id_token expression ({}) has evaluated to null", idToken);
            return next.handle(new StsContext(context, ""), request);
        }
        resolvedIdToken=resolvedIdToken.replaceAll("Bearer ", "");
        if (cache!=null) {
        	final String issued_token=cache.getIfPresent(resolvedIdToken);
        	if (issued_token!=null) {
        		if (logger.isTraceEnabled()) {
        			logger.trace("get ftrom cache {}", issued_token);
        		}
        		return next.handle(new StsContext(context, issued_token), request);
        	}
        }
        final Request transformationRequest=transformationRequest(resolvedIdToken);
        return handler.handle(context, transformationRequest).thenAsync(processIssuedToken(resolvedIdToken, context, request, next));
    }

    private AsyncFunction<Response, Response, NeverThrowsException> processIssuedToken(final String resolvedIdToken,
    																				   final Context context,
                                                                                       final Request request,
                                                                                       final Handler next) {
        return new AsyncFunction<Response, Response, NeverThrowsException>() {
            @Override
            public Promise<Response, NeverThrowsException> apply(final Response response) {
                try {
                    Map<String, Object> json = parseJsonObject(response);
                    String token=null;
                    if (response.getStatus() != Status.OK) {
                        logger.debug("Server side error ({}, {}) while transforming id_token:{}",response.getStatus(),json.get("reason"),json.get("message"));
                    }else {
                    	token = (String) json.get("issued_token");
                    }
                    if (token==null) {
                    	token="";
                    }
                    if (cache!=null) {
                    	cache.put(resolvedIdToken, token);
                    }
                    return next.handle(new StsContext(context, token), request); 
                } catch (IOException e) {
                    logger.error("Can't get JSON back from {}", endpoint, e);
                    return newResponsePromise(newInternalServerError(e));
                }
            }

            @SuppressWarnings("unchecked")
            private Map<String, Object> parseJsonObject(final Response response) throws IOException {
                return (Map<String, Object>) response.getEntity().getJson();
            }
        };
    }

    private Request transformationRequest(final String resolvedIdToken) {
        return new Request().setUri(endpoint)
                            .setMethod("POST")
                            .setEntity(transformation(resolvedIdToken,from,to));
    }

    static Object from(String idToken, String from) {
    	if ("OPENIDCONNECT".equals(from))
    		return object(field("token_type", from),field("oidc_id_token", idToken));
    	else if ("OPENAM".equals(from))
    		return object(field("token_type", from),field("session_id", idToken));
    	else 
    		return object(field("token_type", from),field("session_id", idToken)); //TODO check other types
    }
    
    private static Object transformation(String idToken, String from,String to) {
        return object(field("input_token_state", from(idToken,from)),
        			field("output_token_state", object(field("token_type", to),field("subject_confirmation", "BEARER"),field("nonce", UUID.randomUUID()),field("allow_access", true))));
    }

    /** Creates and initializes a token transformation filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

    	Cache<String, String> cache = null;
    	
        @Override
        public Object create() throws HeapException {
            Handler amHandler = config.get("amHandler").defaultTo(FORGEROCK_CLIENT_HANDLER_HEAP_KEY)
                                                       .as(requiredHeapObject(heap, Handler.class));
            URI openamUri = config.get("openamUri").as(evaluatedWithHeapProperties())
                                                   .required()
                                                   .as(slashEnded())
                                                   .as(uri());
            String realm = config.get("realm").as(evaluatedWithHeapProperties()).defaultTo("/").asString();
            String ssoTokenHeader = config.get("ssoTokenHeader").as(evaluatedWithHeapProperties()).asString();

            Expression<String> idToken = config.get("idToken").required().as(expression(String.class));
            String from=config.get("from").as(evaluatedWithHeapProperties()).defaultTo("OPENIDCONNECT").asString();
            String to=config.get("to").as(evaluatedWithHeapProperties()).defaultTo("SAML2").asString();
            String instance = config.get("instance").as(evaluatedWithHeapProperties()).required().asString();
            
            final Number cacheTtl=config.get("cache-ttl").as(evaluatedWithHeapProperties()).defaultTo(0).asNumber();
            if (cacheTtl.longValue()>0) {
	            cache=CacheBuilder.newBuilder()
					.maximumSize(config.get("cache-size").as(evaluatedWithHeapProperties()).defaultTo(32000).asLong())
					.expireAfterWrite(cacheTtl.longValue(), TimeUnit.MILLISECONDS)
					.recordStats()
					.build();
            }
            if (config.get("username").as(evaluatedWithHeapProperties()).asString()==null)
            	 return new TokenTransformationFilter(amHandler,transformationEndpoint(openamUri, realm, instance),idToken,from,to,cache);
            return new TokenTransformationFilter(Handlers.chainOf(amHandler, new HeadlessAuthenticationFilter(amHandler,openamUri,realm,ssoTokenHeader,config.get("username").as(evaluatedWithHeapProperties()).asString(),config.get("password").as(evaluatedWithHeapProperties()).asString())),
                                                 transformationEndpoint(openamUri, realm, instance),
                                                 idToken,from,to,cache);
        }

        private static URI transformationEndpoint(final URI baseUri, final String realm, final String instance)
                throws HeapException {
            try {
                StringBuilder sb = new StringBuilder("rest-sts");
                if (!realm.startsWith("/")) {
                    sb.append("/");
                }
                sb.append(realm);
                if (!realm.endsWith("/") && !instance.startsWith("/")) {
                    sb.append("/");
                }
                sb.append(instance);
                sb.append("?_action=translate");

                return baseUri.resolve(new URI(sb.toString()));
            } catch (URISyntaxException e) {
                throw new HeapException("Can't build STS endpoint URI", e);
            }
        }
    }
}
