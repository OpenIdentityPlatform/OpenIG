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

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DelegateHeapletTest {

    private final Name heapName = Name.of(getClass().getName());
    private HeapImpl heap;
    private Object object;

    @BeforeMethod
    public void beforeMethod() throws Exception {
        heap = buildDefaultHeap();
        object = new Object();
        heap.put("myObject", object);
    }

    @Test
    public void shouldReturnTheDelegateObject() throws Exception {
        JsonValue config = json(object(field("delegate", "myObject")));
        Object created = new DelegateHeaplet().create(heapName, config, heap);

        assertThat(created).isSameAs(object);
    }

    @DataProvider
    public static Object[][] invalidConfigurations() {
        //@Checkstyle:off
        return new Object[][]{
                { json(object(field("delegate", "fooBarQuix"))) },
                { json(object()) }
        };
        //@Checkstyle:on
    }

    @Test(dataProvider = "invalidConfigurations", expectedExceptions = JsonValueException.class)
    public void shouldFailOnInvalidConfigurations(JsonValue config) throws Exception {
        new DelegateHeaplet().create(heapName, config, heap);
    }
}
