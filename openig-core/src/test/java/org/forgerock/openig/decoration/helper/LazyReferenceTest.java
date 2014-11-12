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

package org.forgerock.openig.decoration.helper;

import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.decoration.helper.LazyReference.*;
import static org.mockito.Mockito.*;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class LazyReferenceTest {

    public static final JsonValue REFERENCE = json("Guillaume");

    @Mock
    private Heap heap;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldResolveOptionalReference() throws Exception {
        LazyReference<String> ref = newReference(heap, REFERENCE, String.class, true);
        ref.get();
        verify(heap).resolve(REFERENCE, String.class, true);
    }

    @Test
    public void shouldResolveMandatoryReference() throws Exception {
        LazyReference<String> ref = newReference(heap, REFERENCE, String.class, false);
        ref.get();
        verify(heap).resolve(REFERENCE, String.class, false);
    }

    @Test(expectedExceptions = HeapException.class)
    public void shouldPropagateException() throws Exception {
        when(heap.resolve(REFERENCE, String.class, false)).thenThrow(new HeapException("Boom"));
        LazyReference<String> ref = newReference(heap, REFERENCE, String.class, false);
        ref.get();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailForNullHeap() throws Exception {
        newReference(null, REFERENCE, String.class, true);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailForNullJsonValue() throws Exception {
        newReference(heap, null, String.class, true);
    }
}
