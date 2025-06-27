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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.decoration.global;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.JsonValue.*;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.heap.Heap;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class GlobalDecoratorTest {

    @Mock
    private Context context;

    @Mock
    private Heap heap;

    @Mock
    private Decorator decorator;

    @Captor
    private ArgumentCaptor<JsonValue> captor;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(context.getHeap()).thenReturn(heap);
        when(decorator.accepts(nullable(Class.class))).thenReturn(true);
    }

    @Test
    public void shouldLookupDecoratorsBasedOnAttributesNames() throws Exception {
        JsonValue decorations = json(object(field("deco-1", true),
                                            field("deco-2", "value")));
        GlobalDecorator globalDecorator = new GlobalDecorator(null, decorations);
        globalDecorator.decorate(new Object(), null, context);

        verify(heap).get("deco-1", Decorator.class);
        verify(heap).get("deco-2", Decorator.class);
    }

    @Test
    public void shouldLookupInheritedDecoratorsBasedOnAttributesNames() throws Exception {
        JsonValue decorations1 = json(object(field("deco-1", true)));
        GlobalDecorator parentGlobalDecorator = new GlobalDecorator(null, decorations1);

        JsonValue decorations2 = json(object(field("deco-2", "value")));
        GlobalDecorator globalDecorator = new GlobalDecorator(parentGlobalDecorator, decorations2);

        globalDecorator.decorate(new Object(), null, context);

        verify(heap).get("deco-1", Decorator.class);
        verify(heap).get("deco-2", Decorator.class);
    }

    @Test
    public void shouldCallDecorators() throws Exception {
        when(heap.get("deco-1", Decorator.class)).thenReturn(decorator);
        when(heap.get("deco-2", Decorator.class)).thenReturn(decorator);

        JsonValue decorations = json(object(field("deco-1", "value-1"),
                                            field("deco-2", "value-2")));
        GlobalDecorator globalDecorator = new GlobalDecorator(null, decorations);
        Object delegate = new Object();
        globalDecorator.decorate(delegate, null, context);

        verify(decorator, times(2)).decorate(anyObject(), captor.capture(), eq(context));
        // Verify that the JSonValues are the one we're expecting
        assertThat(captor.getAllValues().get(0).asString()).isEqualTo("value-1");
        assertThat(captor.getAllValues().get(1).asString()).isEqualTo("value-2");
    }

    @Test
    public void shouldIgnoreMissingDecorators() throws Exception {
        when(heap.get("deco-2", Decorator.class)).thenReturn(decorator);

        JsonValue decorations = json(object(field("deco-1", "value-1"),
                                            field("deco-2", "value-2")));
        GlobalDecorator globalDecorator = new GlobalDecorator(null, decorations);
        Object delegate = new Object();
        globalDecorator.decorate(delegate, null, context);

        verify(decorator).decorate(anyObject(), captor.capture(), eq(context));
        // Verify that the JSonValues are the one we're expecting
        assertThat(captor.getAllValues().get(0).asString()).isEqualTo("value-2");
    }

    @Test
    public void shouldIgnoreIncompatibleDecorators() throws Exception {
        when(heap.get("deco-1", Decorator.class)).thenReturn(decorator);
        when(heap.get("deco-2", Decorator.class)).thenReturn(decorator);
        // deco-1 will not accept the given type (the incompatible one)
        when(decorator.accepts(nullable(Class.class))).thenReturn(false, true);

        JsonValue decorations = json(object(field("deco-1", "value-1"),
                                            field("deco-2", "value-2")));
        GlobalDecorator globalDecorator = new GlobalDecorator(null, decorations);
        Object delegate = new Object();
        globalDecorator.decorate(delegate, null, context);

        verify(decorator).decorate(anyObject(), captor.capture(), eq(context));
        // Verify that the JSonValues are the one we're expecting
        assertThat(captor.getAllValues().get(0).asString()).isEqualTo("value-2");
    }

    @Test
    public void shouldIgnoreReservedFieldNames() throws Exception {
        JsonValue decorations = json(object(field("reserved", true), field("deco", "value")));
        GlobalDecorator globalDecorator = new GlobalDecorator(null, decorations, "reserved");

        globalDecorator.decorate(new Object(), null, context);

        verify(heap).get("deco", Decorator.class);
        verifyNoMoreInteractions(heap);
    }
}
