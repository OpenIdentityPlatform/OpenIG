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
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.io.TemporaryStorage.*;
import static org.forgerock.openig.log.LogSink.*;

import java.io.InputStreamReader;
import java.io.Reader;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.domain.ReferencedObject;
import org.forgerock.openig.heap.domain.TheOne;
import org.forgerock.openig.heap.domain.UseListOfReferences;
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
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-object-creation.json"));

        HeapObject heapObject = (HeapObject) heap.get("heap-object");
        assertThat(heapObject).isNotNull();

        heap.destroy();
        assertThat(heapObject.destroyed).isTrue();
    }

    @Test
    public void testHeapObjectOfSameTypeCreationDestruction() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-object-creation-same-type.json"));

        HeapObject heapObject = (HeapObject) heap.get("heap-object");
        assertThat(heapObject.message).isEqualTo("one");
        HeapObject heapObject2 = (HeapObject) heap.get("heap-object-2");
        assertThat(heapObject2.message).isEqualTo("two");

        heap.destroy();
        assertThat(heapObject.destroyed).isTrue();
        assertThat(heapObject2.destroyed).isTrue();
    }

    @Test
    public void testGetRequiredObjectWithInlineDeclaration() throws Exception {
        JsonValue declaration = json(object(
                field("name", "object"),
                field("type", "org.forgerock.openig.heap.HeapObject"),
                field("config", object())));
        HeapImpl heap = buildDefaultHeap();

        HeapObject object = heap.getRequiredObject(declaration, HeapObject.class);

        assertThat(object).isNotNull();

        // Verify the runtime added object is destroyed
        heap.destroy();
        assertThat(object.destroyed).isTrue();
    }

    @Test
    public void testInlinedDeclarationsCanBeUsedTransparentlyFromHeaplet() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-inline-declaration.json"));

        Reference useNormalRef = (Reference) heap.get("use-normal-ref");
        assertThat(useNormalRef.getObject().message).isEqualTo("referenced");

        Reference useInlineRef = (Reference) heap.get("use-inline-ref");
        assertThat(useInlineRef.getObject().message).isEqualTo("inlined");

        heap.destroy();
        assertThat(useNormalRef.getObject().destroyed).isTrue();
        assertThat(useInlineRef.getObject().destroyed).isTrue();
    }

    @Test
    public void testEncapsulatedDeclarations() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-encapsulated-declaration.json"));

        TheOne neo = (TheOne) heap.get("neo");
        assertThat(neo.matrix).isNotNull();
        assertThat(neo.matrix.architect).isNotNull();
        assertThat(neo.matrix.architect.name).isEqualTo("The Great Architect");
    }

    @Test
    public void testListedInlineDeclarations() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-listed-inline-declaration.json"));

        UseListOfReferences neo = (UseListOfReferences) heap.get("top-level");
        assertThat(neo.references).hasSize(2);

        ReferencedObject ref1 = neo.references.get(0);
        assertThat(ref1.name).isNull();

        ReferencedObject ref2 = neo.references.get(1);
        assertThat(ref2.name).isEqualTo("buried-down-object");
    }

    private JsonValue asJson(final String resourceName) throws Exception {
        Reader reader = new InputStreamReader(getClass().getResourceAsStream(resourceName));
        JSONParser parser = new JSONParser();
        return new JsonValue(parser.parse(reader)).get("heap");
    }

    private HeapImpl buildDefaultHeap() {
        HeapImpl heap = new HeapImpl();
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
        heap.put(LOGSINK_HEAP_KEY, new NullLogSink());
        return heap;
    }
}
