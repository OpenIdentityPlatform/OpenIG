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

package org.forgerock.openig.handler.router;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.forgerock.json.resource.QueryResponse.NO_COUNT;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.util.query.QueryFilter;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RoutesCollectionProviderTest {

    @Mock
    private RouterHandler router;

    @Mock
    private Route routeA;

    @Mock
    private Route routeB;

    @Mock
    private QueryResourceHandler queryResourceHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldRejectQueriesWithQueryExpression() throws Exception {
        RoutesCollectionProvider provider = new RoutesCollectionProvider(router);

        QueryRequest request = Requests.newQueryRequest("routes")
                                       .setQueryExpression("an expression");

        try {
            provider.queryCollection(null, request, queryResourceHandler).getOrThrow();
            failBecauseExceptionWasNotThrown(ResourceException.class);
        } catch (ResourceException e) {
            assertThat(e).isInstanceOf(NotSupportedException.class);
        }
        verifyNoMoreInteractions(queryResourceHandler);
    }

    @Test
    public void shouldRejectQueriesWithQueryId() throws Exception {
        RoutesCollectionProvider provider = new RoutesCollectionProvider(router);

        QueryRequest request = Requests.newQueryRequest("routes")
                                       .setQueryId("an-id");

        try {
            provider.queryCollection(null, request, queryResourceHandler).getOrThrow();
            failBecauseExceptionWasNotThrown(ResourceException.class);
        } catch (ResourceException e) {
            assertThat(e).isInstanceOf(NotSupportedException.class);
        }
        verifyNoMoreInteractions(queryResourceHandler);
    }

    @Test
    public void shouldRejectQueriesWithoutTrueQueryFilter() throws Exception {
        RoutesCollectionProvider provider = new RoutesCollectionProvider(router);

        QueryRequest request = Requests.newQueryRequest("routes")
                                       .setQueryFilter(QueryFilter.<JsonPointer>alwaysFalse());

        try {
            provider.queryCollection(null, request, queryResourceHandler).getOrThrow();
            failBecauseExceptionWasNotThrown(ResourceException.class);
        } catch (ResourceException e) {
            assertThat(e).isInstanceOf(NotSupportedException.class);
        }
        verifyNoMoreInteractions(queryResourceHandler);
    }

    @Test
    public void shouldReturnJsonForTwoRoutes() throws Exception {
        when(router.getRoutes()).thenReturn(asList(routeA, routeB));

        RoutesCollectionProvider provider = new RoutesCollectionProvider(router);

        QueryRequest request = Requests.newQueryRequest("routes")
                                       .setQueryFilter(QueryFilter.<JsonPointer>alwaysTrue());

        QueryResponse response = provider.queryCollection(null, request, queryResourceHandler).get();

        assertThat(response.getTotalPagedResults()).isEqualTo(NO_COUNT);
        verify(queryResourceHandler, times(2)).handleResource(nullable(ResourceResponse.class));
    }
}
