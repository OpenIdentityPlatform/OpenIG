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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.heap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;

import org.forgerock.openig.heap.domain.Author;
import org.forgerock.openig.heap.domain.AuthorHeaplet;
import org.forgerock.openig.heap.domain.Book;
import org.forgerock.openig.heap.domain.Book2;
import org.forgerock.openig.heap.domain.Book3;
import org.forgerock.openig.heap.domain.Book4;
import org.forgerock.openig.heap.domain.Editor;
import org.forgerock.openig.heap.domain.EditorHeapletFactory;
import org.forgerock.openig.heap.domain.Publisher;
import org.forgerock.openig.io.TemporaryStorage;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class HeapletsTest {

    private HeapImpl heap;

    @BeforeMethod
    public void setUp() throws Exception {
        heap = new HeapImpl();
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
    }

    @Test
    public void shouldUseInnerClassHeaplet() throws Exception {
        Heaplet heaplet = Heaplets.getHeaplet(Book.class);

        assertThat(heaplet)
                .isNotNull()
                .isInstanceOf(Book.Heaplet.class);
        assertThat(heaplet.create(Name.of("Book"), json(object()), heap))
                .isInstanceOf(Book.class);
    }

    @Test
    public void shouldUsePrefixedClassHeaplet() throws Exception {
        Heaplet heaplet = Heaplets.getHeaplet(Author.class);

        assertThat(heaplet)
                .isNotNull()
                .isInstanceOf(AuthorHeaplet.class);
        assertThat(heaplet.create(Name.of("Author"), json(object()), heap))
                .isInstanceOf(Author.class);
    }

    @Test
    public void shouldUseHeapletFactory() throws Exception {
        Heaplet heaplet = Heaplets.getHeaplet(Editor.class);

        assertThat(heaplet)
                .isNotNull()
                .isInstanceOf(EditorHeapletFactory.EditorHeaplet.class);
        assertThat(heaplet.create(Name.of("Editor"), json(object()), heap))
                .isInstanceOf(Editor.class);
    }

    @Test
    public void shouldUseGivenHeapletClass() throws Exception {
        Heaplet heaplet = Heaplets.getHeaplet(EditorHeapletFactory.EditorHeaplet.class);

        assertThat(heaplet)
                .isNotNull()
                .isInstanceOf(EditorHeapletFactory.EditorHeaplet.class);
        assertThat(heaplet.create(Name.of("Editor"), json(object()), heap))
                .isInstanceOf(Editor.class);
    }

    @Test
    public void shouldFailToFindAnyCompatibleHeaplet() throws Exception {
        // no inner class, prefixed or heaplet factory found
        assertThat(Heaplets.getHeaplet(Publisher.class)).isNull();

        // inner class is not static
        assertThat(Heaplets.getHeaplet(Book2.class)).isNull();

        // inner class is protected (not public)
        assertThat(Heaplets.getHeaplet(Book3.class)).isNull();

        // inner class is private (not public)
        assertThat(Heaplets.getHeaplet(Book4.class)).isNull();
    }
}
