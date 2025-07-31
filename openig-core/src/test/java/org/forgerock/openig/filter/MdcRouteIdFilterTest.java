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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.MDC;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MdcRouteIdFilterTest {

    private static final String ROUTE_NAME = "my-route";
    @Mock
    private Handler handler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testDownstreamHandlerHasTheMessageDiagnosisContextCorrectlySet() throws Exception {
        MdcRouteIdFilter filter = new MdcRouteIdFilter(ROUTE_NAME);

        when(handler.handle(nullable(Context.class), nullable(Request.class)))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(InvocationOnMock invocation) {
                        // MDC is set only within this execution
                        assertThat(MDC.get("routeId")).isEqualTo(ROUTE_NAME);
                        return newResponsePromise(new Response(Status.OK));
                    }
                });

        final String outerRouteId = "my-enclosing-router";
        MDC.put("routeId", outerRouteId);

        // 2 routes can be enclosed : a Router can be defined as a Route, so it sets first its routeId in the MDC
        assertThat(MDC.get("routeId")).isEqualTo(outerRouteId);

        filter.filter(new RootContext(), new Request(), handler);

        // MDC contains the same value than before being filtered
        assertThat(MDC.get("routeId")).isEqualTo(outerRouteId);
    }

}
