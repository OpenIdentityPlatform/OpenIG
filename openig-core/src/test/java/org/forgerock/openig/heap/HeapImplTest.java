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

package org.forgerock.openig.heap;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.io.TemporaryStorage.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.log.LogSink.LOGSINK_HEAP_KEY;

import java.io.InputStreamReader;
import java.io.Reader;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.NullLogSink;
import org.json.simple.parser.JSONParser;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class HeapImplTest {
    @Test
    public void testPutAndGetObjectLocally() throws Exception {
        HeapImpl heap = new HeapImpl();
        heap.put("Open", "IG");
        assertThat(heap.get("Open")).isEqualTo("IG");
    }

    @Test
    public void testPutAndGetObjectInHierarchy() throws Exception {
        HeapImpl parent = new HeapImpl();
        parent.put("Open", "IG");

        HeapImpl child = new HeapImpl(parent);

        assertThat(child.get("Open")).isEqualTo("IG");
    }

    @Test
    public void testPutAndGetOverriddenObjectInHierarchy() throws Exception {
        HeapImpl parent = new HeapImpl();
        parent.put("Open", "IG");

        HeapImpl child = new HeapImpl(parent);
        parent.put("Open", "AM");

        assertThat(child.get("Open")).isEqualTo("AM");
    }

    @Test
    public void testHeapObjectCreationDestruction() throws Exception {
        HeapImpl heap = new HeapImpl();
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
        heap.put(LOGSINK_HEAP_KEY, new NullLogSink());
        heap.init(asJson("heap-object-creation.json"));

        HeapObject heapObject = (HeapObject) heap.get("heap-object");
        assertThat(heapObject).isNotNull();

        heap.destroy();
        assertThat(heapObject.destroyed).isTrue();
    }

    @Test
    public void testHeapObjectOfSameTypeCreationDestruction() throws Exception {
        HeapImpl heap = new HeapImpl();
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
        heap.put(LOGSINK_HEAP_KEY, new NullLogSink());
        heap.init(asJson("heap-object-creation-same-type.json"));

        HeapObject heapObject = (HeapObject) heap.get("heap-object");
        assertThat(heapObject.message).isEqualTo("one");
        HeapObject heapObject2 = (HeapObject) heap.get("heap-object-2");
        assertThat(heapObject2.message).isEqualTo("two");

        heap.destroy();
        assertThat(heapObject.destroyed).isTrue();
        assertThat(heapObject2.destroyed).isTrue();
    }

    private JsonValue asJson(final String resourceName) throws Exception {
        Reader reader = new InputStreamReader(getClass().getResourceAsStream(resourceName));
        JSONParser parser = new JSONParser();
        return new JsonValue(parser.parse(reader)).get("heap");
    }
}
