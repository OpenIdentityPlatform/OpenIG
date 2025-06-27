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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.http.protocol.Status.FORBIDDEN;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.util.Options.defaultOptions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import org.forgerock.http.Handler;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConditionEnforcementFilterTest {

    private Context context;

    @Mock
    private Handler failureHandler;

    @Mock
    private Handler next;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        context = new RootContext();
    }
    @DataProvider
    private static Object[][] validConfigurations() {
        return new Object[][] {
            { json(object(
                    field("condition", "${false}"))) },
            { json(object(
                    field("condition", "${true}"),
                    field("failureHandler", "failureHandler"))) } };
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldCreateHeaplet(final JsonValue config) throws Exception {
        final ConditionEnforcementFilter.Heaplet heaplet = new ConditionEnforcementFilter.Heaplet();
        final ConditionEnforcementFilter filter = (ConditionEnforcementFilter) heaplet.create(Name.of("myFilter"),
                                                                                              config,
                                                                                              buildDefaultHeap());
        assertThat(filter).isNotNull();
    }

    @DataProvider
    private static Object[][] invalidConfigurations() {
        return new Object[][] {
            /** Missing condition attribute. */
            { json(object(
                    field("conditiontypo", "${false}"))) },
            /** Invalid condition attribute. */
            { json(object(
                    field("condition", 3.141592))) },
            /** Invalid failure handler. */
            { json(object(
                    field("condition", "${true}"),
                    field("failureHandler", "unknownInHeap"))) },
            /** Invalid value for the failure handler. */
            { json(object(
                    field("condition", "${true}"),
                    field("failureHandler", 42))) } };
    }

    @Test(dataProvider = "invalidConfigurations", expectedExceptions = JsonValueException.class)
    public void shouldNotCreateHeaplet(final JsonValue invalidConfiguration) throws Exception {
        final ConditionEnforcementFilter.Heaplet heaplet = new ConditionEnforcementFilter.Heaplet();
        heaplet.create(Name.of("myFilter"), invalidConfiguration, buildDefaultHeap());
    }

    @Test
    public void shouldContinueChainExecutionWhenConditionIsTrue() throws Exception {
        final ConditionEnforcementFilter filter = new ConditionEnforcementFilter(Expression.valueOf("${true}",
                                                                                                    Boolean.class));
        filter.filter(context, null, next);
        verify(next).handle(any(Context.class), any());
    }

    @DataProvider
    public static Object[][] conditionsEvaluatingToFalse() {
        return new Object[][] {
            { "${false}" },
            { "not a condition" },
            { "${attributes.missing}" }
        };
    }

    @Test(dataProvider = "conditionsEvaluatingToFalse")
    public void shouldReturnForbiddenAndBreakChainExecutionWhenConditionIsFalse(final String condition)
            throws Exception {
        // Given
        final ConditionEnforcementFilter filter = new ConditionEnforcementFilter(Expression.valueOf(condition,
                                                                                                    Boolean.class));
        // When
        final Response response = filter.filter(context, null, next).get();
        // Then
        assertThat(response.getStatus()).isEqualTo(FORBIDDEN);
        assertThat(response.getCause()).isNull();
        verifyNoMoreInteractions(next);
    }

    @Test(dataProvider = "conditionsEvaluatingToFalse")
    public void shouldDelegateToFailureHandlerForInvalidOrEvaluatedToFalseConditions(final String condition)
            throws Exception {
        // Given
        final ConditionEnforcementFilter filter = new ConditionEnforcementFilter(Expression.valueOf(condition,
                                                                                                    Boolean.class),
                                                                                 failureHandler);
        // When
        filter.filter(context, null, next);
        // Then
        verify(failureHandler).handle(any(Context.class), any());
    }

    private HeapImpl buildDefaultHeap() throws Exception {
        final HeapImpl heap = new HeapImpl(Name.of("myHeap"));
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, newTemporaryStorage());
        heap.put(CLIENT_HANDLER_HEAP_KEY, new ClientHandler(new HttpClientHandler(defaultOptions())));
        heap.put("failureHandler", failureHandler);
        return heap;
    }
}
