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

package org.forgerock.openig.heap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.RouteMatchers;
import org.forgerock.http.routing.Router;
import org.forgerock.http.routing.RoutingMode;
import org.forgerock.openig.handler.Handlers;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class GenericHeapletTest {

    private HeapImpl heap;
    private Router router;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.heap = buildDefaultHeap();
        router = new Router();
        router.addRoute(RouteMatchers.requestUriMatcher(RoutingMode.EQUALS, ""), Handlers.NO_CONTENT);
        heap.put(Keys.ENDPOINT_REGISTRY_HEAP_KEY, new EndpointRegistry(router, ""));
    }

    @Test
    public void shouldCreateSpecificEndpointRegistryLazily() throws Exception {
        // Before creation: not found
        assertThat(router.handle(new RootContext(), new Request().setUri("/this")).get().getStatus())
                .isEqualTo(Status.NOT_FOUND);

        // Register a 'this' object
        GenericHeaplet heaplet = new UseEndpointRegistryHeaplet();
        heaplet.create(Name.of("this"), json(object()), heap);

        // After creation: namespace is available ...
        assertThat(router.handle(new RootContext(), new Request().setUri("/this")).get().getStatus())
                .isEqualTo(Status.NO_CONTENT);

        // ... and endpoint is here too
        assertThat(router.handle(new RootContext(), new Request().setUri("/this/hello")).get().getStatus())
                .isEqualTo(Status.TEAPOT);

        heaplet.destroy();

        // After destroy: not found again
        assertThat(router.handle(new RootContext(), new Request().setUri("/this")).get().getStatus())
                .isEqualTo(Status.NOT_FOUND);
    }

    private final class UseEndpointRegistryHeaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            endpointRegistry().register("hello", new Handler() {
                @Override
                public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
                    return Response.newResponsePromise(new Response(Status.TEAPOT));
                }
            });
            return "Hello";
        }
    }
}
