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

package org.forgerock.openig.filter;

import static java.lang.Boolean.TRUE;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.openig.util.MessageType.RESPONSE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.regex.PatternTemplate;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

/**
 * Supports password replay feature in a composite filter.
 * Two use cases are supported:
 * <ul>
 *     <li>Replaying credentials when a login page is <em>queried</em> (on the request flow)</li>
 *     <li>Replaying credentials when a login page is <em>returned</em> (on the response flow)</li>
 * </ul>
 *
 * <p>A variation on the first case is possible: it can let the request flow and extract values from the server's
 * response.
 *
 * <p>This filter supports value extraction for any server-provided values that would be re-used in the
 * authentication request.
 *
 * <p>Credentials must be installed by a filter that will only be placed in a chain when needed (in case they're not
 * already there). If required (and if the credentials are available as request headers), we can decrypt them using a
 * {@link CryptoHeaderFilter}.
 *
 * <p>Then an authentication request is built (using a {@link StaticRequestFilter}) and sent down the chain.
 *
 * Note that:
 * <ul>
 *     <li>There is no retry in case of authentication failure</li>
 *     <li>Extracted patterns need to have at least a single regex group, the first one is always used</li>
 * </ul>
 *
 * <h3>Usage examples:</h3>
 *
 * <h4>Authenticate on behalf of the user when a login page is GET</h4>
 *
 * <p>When a {@literal GET} request to the login page is intercepted, OpenIG will generate an alternative
 * authentication request and send it in place of the original request. The response is forwarded as-is to the caller.
 * All other requests are forwarded untouched.
 *
 * <pre>
 *     {@code {
 *         "loginPage": "${matches(request.uri.path, '/login') and (request.method == 'GET')}",
 *         "request": {
 *           "method": "POST",
 *           "uri": "http://internal.example.com/login",
 *           "form": {
 *             "username": [ "${contexts.attributes.attributes.username}" ],
 *             "password": [ "${contexts.attributes.attributes.password}" ]
 *           }
 *         }
 *       }
 *     }
 * </pre>
 *
 * <h4>Authenticate on behalf of the user when a login page is returned</h4>
 *
 * <p>When a response that is identified to be a login page is intercepted, OpenIG will generate an authentication
 * request and send it. The authentication response is ignored ATM. Then OpenIG replays the original incoming request.
 *
 * <pre>
 *     {@code {
 *         "loginPageContentMarker": "I'm a login page",
 *         "request": {
 *           "method": "POST",
 *           "uri": "http://internal.example.com/login",
 *           "headers": {
 *             "X-OpenAM-Username": [ "${contexts.attributes.attributes.username}" ],
 *             "X-OpenAM-Password": [ "${contexts.attributes.attributes.password}" ]
 *           }
 *         }
 *       }
 *     }
 * </pre>
 *
 * <h2>Options</h2>
 *
 * <h3>Obtain credentials</h3>
 *
 * The PasswordReplay Filter can be configured (with the {@code credentials} attribute) to invoke an additional
 * Filter that would be responsible to obtain credentials and make them available in the request processing data
 * structures. These values can then be used to create an appropriate authentication request.
 *
 * <p>The {@code credentials} attribute expects a reference to a {@link Filter} heap object.
 *
 * <p>Examples of such filters can be {@link FileAttributesFilter} (to load credentials from a local CSV file)
 * or {@link SqlAttributesFilter} (to load credentials from a database).
 *
 * <pre>
 *     {@code {
 *         "loginPageContentMarker": "I'm a login page",
 *         "credentials": {
 *             "type": "FileAttributesFilter",
 *             "config": {
 *                 "file": "${system.home}/users.csv",
 *                 "key": "uid",
 *                 "value": "${contexts.attributes.attributes.whoami}",
 *                 "target": "${contexts.attributes.attributes.user}"
 *             }
 *         }
 *         "request": {
 *           "method": "POST",
 *           "uri": "http://internal.example.com/login",
 *           "headers": {
 *             "X-OpenAM-Username": [ "${contexts.attributes.attributes.user.uid}" ],
 *             "X-OpenAM-Password": [ "${contexts.attributes.attributes.user.password}" ]
 *           }
 *         }
 *       }
 *     }
 * </pre>
 *
 * <h3>Extract custom values from intercepted response page</h3>
 *
 * It may happen that the login page contains a form with hidden fields that will be send back to the IDP when submit
 * button will be hit.
 * As this filter doesn't interpret the returned page content and generate a new authentication request, it needs a
 * way to extract some values from the response's entity.
 *
 * <p>Multiple values can be extracted at once, extraction is based on pattern matching (and use a
 * {@link EntityExtractFilter} under the hood).
 * As opposed to the {@literal EntityExtractFilter}, only 1 group is supported, and matched group value is placed in
 * the results. All extracted values will be placed in a Map available in
 * {@literal contexts.attributes.attributes.extracted}.
 *
 * <pre>
 *     {@code {
 *         "loginPageContentMarker": "I'm a login page",
 *         "loginPageExtractions": [
 *             {
 *                 "name": "nonce",
 *                 "pattern": " nonce='(.*)'"
 *             }
 *         ],
 *         "request": {
 *           "method": "POST",
 *           "uri": "http://internal.example.com/login",
 *           "form": {
 *             "username": [ "${contexts.attributes.attributes.username}" ],
 *             "password": [ "${contexts.attributes.attributes.password}" ]
 *             "nonce": [ "${contexts.attributes.attributes.extracted.nonce}" ]
 *           }
 *         }
 *       }
 *     }
 * </pre>
 *
 * <h3>Decrypt values provided as request headers</h3>
 *
 * When using an OpenAM policy agent in front of OpenIG, the agent configured to retrieve username and password, the
 * request will contains encrypted headers.
 * For replaying them, this filter needs to decrypt theses values before creating the authentication request.
 *
 * <p>This filter use a {@link CryptoHeaderFilter} to do the decryption of values. Note that it only decrypts and
 * always acts on the request flow. All other attributes are the same as those used for configuring a normal
 * {@link CryptoHeaderFilter}.
 *
 * <p>Note that this is only one example usage, as soon as there are encrypted values in headers, this function is
 * here to decrypt them in place if needed.
 *
 * <pre>
 *     {@code {
 *         "loginPageContentMarker": "I'm a login page",
 *         "headerDecryption": {
 *             "algorithm": "DES/ECB/NoPadding",
 *             "key": "....",
 *             "keyType": "DES",
 *             "headers": [ "X-OpenAM-Password" ]
 *         },
 *         "request": {
 *           "method": "POST",
 *           "uri": "http://internal.example.com/login",
 *           "form": {
 *             "username": [ "${request.headers['X-OpenAM-Username'][0]}" ],
 *             "password": [ "${request.headers['X-OpenAM-Password'][0]}" ]
 *           }
 *         }
 *       }
 *     }
 * </pre>
 */
public class PasswordReplayFilter extends GenericHeapObject {

    /** Creates and initializes an password-replay filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        static final String IS_LOGIN_PAGE_ATTR = "isLoginPage";
        private EntityExtractFilter extractFilter;
        private StaticRequestFilter createRequestFilter;
        private Expression<Boolean> loginPage;
        private Filter credentialsFilter;
        private CryptoHeaderFilter decryptFilter;

        @Override
        public Object create() throws HeapException {

            boolean hasLoginPageMarker = config.isDefined("loginPageContentMarker");
            boolean hasLoginPage = config.isDefined("loginPage");
            if (!hasLoginPage && !hasLoginPageMarker) {
                throw new HeapException("Either 'loginPage' or 'loginPageContentMarker' (or both) must have a value");
            }

            loginPage = hasLoginPage ? asExpression(config.get("loginPage"), Boolean.class) : null;

            createRequestFilter = (StaticRequestFilter) new StaticRequestFilter.Heaplet()
                    .create(qualified.child("$request-creator"),
                            config.get("request").required(),
                            heap);

            credentialsFilter = heap.resolve(config.get("credentials"), Filter.class, true);

            JsonValue headerDecryption = config.get("headerDecryption");
            if (headerDecryption.isNotNull()) {
                headerDecryption.put("messageType", "request");
                headerDecryption.put("operation", "decrypt");
                decryptFilter = (CryptoHeaderFilter)
                        new CryptoHeaderFilter.Heaplet().create(qualified.child("$decrypt"), headerDecryption, heap);
            }

            extractFilter = null;
            if (hasLoginPageMarker) {
                extractFilter = createEntityExtractFilter();
                extractFilter.getExtractor()
                             .getPatterns()
                             .put(IS_LOGIN_PAGE_ATTR, config.get("loginPageContentMarker").asPattern());
                extractFilter.getExtractor()
                             .getTemplates()
                             .put(IS_LOGIN_PAGE_ATTR, new PatternTemplate("true"));
            }

            for (JsonValue extraction : config.get("loginPageExtractions")) {
                if (extractFilter == null) {
                    extractFilter = createEntityExtractFilter();
                }
                String name = extraction.get("name").required().asString();
                extractFilter.getExtractor()
                             .getPatterns()
                             .put(name, extraction.get("pattern").required().asPattern());
                extractFilter.getExtractor()
                             .getTemplates()
                             .put(name, new PatternTemplate("$1"));
            }

            if (hasLoginPage) {
                if (!config.isDefined("loginPageExtractions")) {
                    // case 1:
                    // a loginPage, but no loginPageExtractions (we don't care about result)
                    return new Filter() {
                        @Override
                        public Promise<Response, NeverThrowsException> filter(final Context context,
                                                                              final Request request,
                                                                              final Handler next) {
                            // Request targeting the login page ?
                            if (isLoginPageRequest(bindings(context, request))) {
                                return authentication(next).handle(context, request);
                            }
                            // pass through
                            return next.handle(context, request);
                        }

                        private Handler authentication(final Handler next) {
                            List<Filter> filters = new ArrayList<>();
                            if (credentialsFilter != null) {
                                filters.add(credentialsFilter);
                            }
                            if (decryptFilter != null) {
                                filters.add(decryptFilter);
                            }
                            filters.add(createRequestFilter);
                            return chainOf(next, filters);
                        }
                    };
                } else {
                    // case 2:
                    // a loginPage, with loginPageExtractions
                    // need to extract values from login page response
                    return new Filter() {
                        @Override
                        public Promise<Response, NeverThrowsException> filter(final Context context,
                                                                              final Request request,
                                                                              final Handler next) {
                            // Request targeting the login page ?
                            if (isLoginPageRequest(bindings(context, request))) {
                                return extractFilter.filter(context, request, next)
                                                    .thenOnResult(markAsLoginPage(context))
                                                    .thenAsync(authenticateIfNeeded(context, request, next, false));
                            }
                            // pass through
                            return next.handle(context, request);
                        }
                    };
                }
            } else {
                // no login page pattern
                // need to intercept all responses
                // TODO maybe we can do that only when not authenticated ?
                // but that would assume that we know when we're no longer logged into the application
                return new Filter() {
                    @Override
                    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                                          final Request request,
                                                                          final Handler next) {

                        // Call the filter responsible for extracting values
                        return extractFilter.filter(context, request, next)
                                            .thenAsync(authenticateIfNeeded(context, request, next, true));
                    }
                };
            }
        }

        private ResultHandler<Response> markAsLoginPage(final Context context) {
            return new ResultHandler<Response>() {
                @Override
                public void handleResult(final Response result) {
                    getExtractedValues(context).put(IS_LOGIN_PAGE_ATTR, "true");
                }
            };
        }

        private boolean isLoginPageRequest(final Bindings bindings) {
            return TRUE.equals(loginPage.eval(bindings));
        }

        private AsyncFunction<Response, Response, NeverThrowsException> authenticateIfNeeded(final Context context,
                                                                                             final Request request,
                                                                                             final Handler next,
                                                                                             final boolean replay) {
            return new AsyncFunction<Response, Response, NeverThrowsException>() {
                @Override
                public Promise<Response, NeverThrowsException> apply(final Response response) {
                    List<Filter> filters = new ArrayList<>();
                    if (credentialsFilter != null) {
                        filters.add(credentialsFilter);
                    }

                    if (decryptFilter != null) {
                        filters.add(decryptFilter);
                    }

                    // values have been extracted
                    Map<String, String> values = getExtractedValues(context);
                    if (values.get(IS_LOGIN_PAGE_ATTR) != null) {
                        // we got a login page, we need to authenticate the user
                        filters.add(createRequestFilter);
                    }

                    // Fast exit
                    if (filters.isEmpty()) {
                        // let the response flow back
                        return newResponsePromise(response);
                    }
                    // Go through the authentication chain
                    Promise<Response, NeverThrowsException> promise = chainOf(next, filters).handle(context, request);
                    if (replay) {
                        return promise.thenAsync(replayOriginalRequest(context, request, next));
                    }
                    return promise;
                }
            };
        }

        private AsyncFunction<Response, Response, NeverThrowsException> replayOriginalRequest(final Context context,
                                                                                              final Request request,
                                                                                              final Handler next) {
            return new AsyncFunction<Response, Response, NeverThrowsException>() {
                @Override
                public Promise<Response, NeverThrowsException> apply(final Response value) {
                    // Ignore response and replay original request
                    return next.handle(context, request);
                }
            };
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> getExtractedValues(final Context context) {
            AttributesContext attributesContext = context.asContext(AttributesContext.class);
            return (Map<String, String>) attributesContext.getAttributes().get("extracted");
        }

        private EntityExtractFilter createEntityExtractFilter() throws HeapException {
            try {
                Expression<Object> target = Expression.valueOf("${contexts.attributes.attributes.extracted}",
                                                               Object.class);
                return new EntityExtractFilter(RESPONSE, target);
            } catch (ExpressionException e) {
                // Should never happen: expression is under control
                throw new HeapException(e);
            }
        }
    }
}
