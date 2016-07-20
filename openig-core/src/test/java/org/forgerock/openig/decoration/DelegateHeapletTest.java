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

package org.forgerock.openig.decoration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;
import static org.mockito.Mockito.mock;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DelegateHeapletTest {

    @Test
    public void shouldReturnTheDelegateObject() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        Object object = mock(Object.class);
        heap.put("myObject", object);

        JsonValue config = json(object(field("delegate", "myObject")));
        Object created = new DelegateHeaplet().create(Name.of("test"), config, heap);

        assertThat(created).isSameAs(object);
    }
}
