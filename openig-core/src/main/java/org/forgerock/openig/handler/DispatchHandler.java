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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import static org.forgerock.json.JsonValueFunctions.uri;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Handler;
import org.forgerock.http.MutableUri;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Responses;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches to one of a list of handlers. When a request is handled, each handler's
 * condition is evaluated. If a condition expression yields {@code true}, then the request
 * is dispatched to the associated handler with no further processing.
 * <p>
 * If no condition yields {@code true} then the handler will return a {@literal 404} not found response.
 * Therefore, it's advisable to have a single "default" handler at the end of the list
 * with no condition (unconditional) to handle otherwise un-dispatched requests.
 */
public class DispatchHandler implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(DispatchHandler.class);

    /** Expressions to evaluate against request and context, bound to handlers to dispatch to. */
    private final List<Binding> bindings = new ArrayList<>();

    /**
     * Binds an expression to the current handler to dispatch to.
     *
     * @param condition
     *            Condition to evaluate to determine if associated handler should be dispatched to. If omitted, then
     *            dispatch is unconditional.
     * @param handler
     *            The name of the handler heap object to dispatch to if the associated condition yields true.
     * @param baseURI
     *            Overrides the existing request URI, making requests relative to a new base URI. Only scheme, host and
     *            port are used in the supplied URI. Default: leave URI untouched.
     * @return The current dispatch handler.
     */
    public DispatchHandler addBinding(Expression<Boolean> condition, Handler handler, URI baseURI) {
        bindings.add(new Binding(condition, handler, baseURI));
        return this;
    }

    /**
     * Adds an unconditional bindings to the handler.
     *
     * @param handler
     *            The name of the handler heap object to dispatch to if the associated condition yields true.
     * @param baseURI
     *            Overrides the existing request URI, making requests relative to a new base URI. Only scheme, host and
     *            port are used in the supplied URI. Default: leave URI untouched.
     * @return The current dispatch handler.
     */
    public DispatchHandler addUnconditionalBinding(Handler handler, URI baseURI) {
        bindings.add(new Binding(null, handler, baseURI));
        return this;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        for (Binding binding : bindings) {
            if (binding.condition == null
                    || Boolean.TRUE.equals(binding.condition.eval(Bindings.bindings(context, request)))) {
                if (binding.baseURI != null) {
                    if (!"".equals(binding.baseURI.getPath()))  {//rebase path
                    	final String query = request.getUri().getRawQuery();
                    	final MutableUri mutableUri = new MutableUri(binding.baseURI);
                    	if (mutableUri.getRawQuery()==null && query!=null) {
	                    	try {
								mutableUri.setRawQuery(query);
							} catch (URISyntaxException e) {
								logger.error("error dispatching to " + binding.baseURI, e);
								return Promises.newResultPromise(Responses.newInternalServerError(e));
							}
                    	}
                    	request.setUri(mutableUri.asURI());
                    }
                    else
                    	request.getUri().rebase(binding.baseURI);
                }
                return binding.handler.handle(context, request);
            }
        }
        logger.error("no handler to dispatch to");
        return Promises.newResultPromise(Responses.newNotFound());
    }

    /** Binds an expression with a handler to dispatch to. */
    private static class Binding {

        /** Condition to dispatch to handler or {@code null} if unconditional. */
        private Expression<Boolean> condition;

        /** Handler to dispatch to. */
        private Handler handler;

        /** Overrides scheme/host/port of the request with a base URI. */
        private URI baseURI;

        /**
         * Constructor.
         *
         * @param condition
         *            Condition to evaluate to determine if associated handler should be dispatched to. If omitted, then
         *            dispatch is unconditional.
         * @param handler
         *            The name of the handler heap object to dispatch to if the associated condition yields true.
         * @param baseURI
         *            Overrides the existing request URI, making requests relative to a new base URI. Only scheme, host
         *            and port are used in the supplied URI. Default: leave URI untouched.
         */
        public Binding(Expression<Boolean> condition, Handler handler, URI baseURI) {
            super();
            this.condition = condition;
            this.handler = handler;
            this.baseURI = baseURI;
        }
    }

    /**
     * Creates and initializes a dispatch handler in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            DispatchHandler dispatchHandler = new DispatchHandler();
            for (JsonValue jv : config.get("bindings").expect(List.class)) {
                jv.required().expect(Map.class);
                final Expression<Boolean> expression = jv.get("condition")
                                                         .as(expression(Boolean.class));
                final Handler handler = jv.get("handler").as(requiredHeapObject(heap, Handler.class));
                final URI uri = jv.get("baseURI").as(evaluatedWithHeapProperties()).as(uri());
                dispatchHandler.addBinding(expression, handler, uri);
            }
            return dispatchHandler;
        }
    }
}
