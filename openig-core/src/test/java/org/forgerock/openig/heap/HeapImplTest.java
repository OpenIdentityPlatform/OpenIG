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
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;
import static org.forgerock.openig.util.JsonValues.readJson;

import java.util.List;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.heap.domain.Architect;
import org.forgerock.openig.heap.domain.Book;
import org.forgerock.openig.heap.domain.DecoratorDecorator;
import org.forgerock.openig.heap.domain.ReferencedObject;
import org.forgerock.openig.heap.domain.TheOne;
import org.forgerock.openig.heap.domain.UseListOfReferences;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class HeapImplTest {

    @Test(description = "OPENIG-329")
    public void shouldAllowEmptyHeap() throws Exception {
        final HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-with-no-objects.json"));
        heap.destroy();
    }

    @Test(description = "OPENIG-380")
    public void shouldAllowLegacyObjectsArray() throws Exception {
        final HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-object-with-legacy-objects-array.json"));
        final HeapObject heapObject = heap.get("CustomHeapObject", HeapObject.class);
        assertThat(heapObject.message).isEqualTo("Custom Message");
        heap.destroy();
    }

    @Test
    public void shouldAllowNoConfigAttribute() throws Exception {
        final HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-object-without-config-attribute.json"));

        assertThat(heap.get("heap-object", HeapObject.class)).isNotNull();

        heap.destroy();
    }

    @Test
    public void shouldAllowNullConfigAttribute() throws Exception {
        final HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-object-with-null-config-attribute.json"));

        assertThat(heap.get("heap-object", HeapObject.class)).isNotNull();

        heap.destroy();
    }

    @Test
    public void shouldAllowEmptyConfigAttribute() throws Exception {
        final HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-object-with-empty-config-attribute.json"));

        assertThat(heap.get("heap-object", HeapObject.class)).isNotNull();

        heap.destroy();
    }

    @Test
    public void testSimpleConfigAttribute() throws Exception {
        final HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-object-with-simple-config-attribute.json"));
        final HeapObject heapObject = heap.get("CustomHeapObject", HeapObject.class);
        assertThat(heapObject.message).isEqualTo("Custom Message");
        heap.destroy();
    }

    @Test(expectedExceptions = JsonValueException.class,
            expectedExceptionsMessageRegExp = ".*Expecting a java\\.util\\.Map")
    public void shouldNotAllowInvalidConfig() throws Exception {

        final HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-object-with-invalid-config-attribute.json"));

    }

    @Test
    public void testPutAndGetObjectLocally() throws Exception {
        HeapImpl heap = new HeapImpl();
        heap.put("Open", "IG");
        assertThat(heap.get("Open", String.class)).isEqualTo("IG");
    }

    @Test
    public void testPutAndGetObjectInHierarchy() throws Exception {
        HeapImpl parent = new HeapImpl();
        parent.put("Open", "IG");

        HeapImpl child = new HeapImpl(parent);

        assertThat(child.get("Open", String.class)).isEqualTo("IG");
    }

    @Test
    public void testPutAndGetOverriddenObjectInHierarchy() throws Exception {
        HeapImpl parent = new HeapImpl();
        parent.put("Open", "IG");

        HeapImpl child = new HeapImpl(parent);
        parent.put("Open", "AM");

        assertThat(child.get("Open", String.class)).isEqualTo("AM");
    }

    @Test
    public void testHeapObjectCreationDestruction() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-object-creation.json"));

        HeapObject heapObject = heap.get("heap-object", HeapObject.class);
        assertThat(heapObject).isNotNull();

        heap.destroy();
        assertThat(heapObject.destroyed).isTrue();
    }

    @Test
    public void testHeapObjectOfSameTypeCreationDestruction() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-object-creation-same-type.json"));

        HeapObject heapObject = heap.get("heap-object", HeapObject.class);
        assertThat(heapObject.message).isEqualTo("one");
        HeapObject heapObject2 = heap.get("heap-object-2", HeapObject.class);
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

        HeapObject object = heap.resolve(declaration, HeapObject.class);

        assertThat(object).isNotNull();

        // Verify the runtime added object is destroyed
        heap.destroy();
        assertThat(object.destroyed).isTrue();
    }

    @Test
    public void testInlinedDeclarationsCanBeUsedTransparentlyFromHeaplet() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-inline-declaration.json"));

        Reference useNormalRef = heap.get("use-normal-ref", Reference.class);
        assertThat(useNormalRef.getObject().message).isEqualTo("referenced");

        Reference useInlineRef = heap.get("use-inline-ref", Reference.class);
        assertThat(useInlineRef.getObject().message).isEqualTo("inlined");

        heap.destroy();
        assertThat(useNormalRef.getObject().destroyed).isTrue();
        assertThat(useInlineRef.getObject().destroyed).isTrue();
    }

    @Test
    public void testEncapsulatedDeclarations() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-encapsulated-declaration.json"));

        TheOne neo = heap.get("neo", TheOne.class);
        assertThat(neo.matrix).isNotNull();
        assertThat(neo.matrix.architect).isNotNull();
        assertThat(neo.matrix.architect.name).isEqualTo("The Great Architect");
    }

    @Test
    public void testListedInlineDeclarations() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-listed-inline-declaration.json"));

        UseListOfReferences neo = heap.get("top-level", UseListOfReferences.class);
        assertThat(neo.references).hasSize(2);

        ReferencedObject ref1 = neo.references.get(0);
        assertThat(ref1.name).isNull();

        ReferencedObject ref2 = neo.references.get(1);
        assertThat(ref2.name).isEqualTo("buried-down-object");
    }

    @Test
    public void testThatPerformingResolutionMultipleTimesReturnsTheSameObject() throws Exception {
        HeapImpl heap = buildDefaultHeap();

        JsonValue bookDefinition = json(object(field("type", Book.class.getName())));
        Book first = heap.resolve(bookDefinition, Book.class);
        Book second = heap.resolve(bookDefinition, Book.class);
        assertThat(first).isSameAs(second);
    }

    @Test
    public void testDecoration() throws Exception {
        HeapImpl heap = buildDefaultHeap();

        heap.put("decorator", new BookDecorator());

        JsonValue withDecoration = json(object(field("type", Book.class.getName()),
                                               field("decorator", "Hey")));
        Book book = heap.resolve(withDecoration, Book.class);

        assertThat(book).isInstanceOf(DecoratedBook.class);
        DecoratedBook decorated = (DecoratedBook) book;
        assertThat(decorated.delegate).isInstanceOf(Book.class);
        assertThat(decorated.decoration.asString()).isEqualTo("Hey");

        // Verify context
        assertThat(decorated.context.getName()).isNotNull();
        assertThat(decorated.context.getHeap()).isSameAs(heap);
        assertThat(decorated.context.getConfig()).isEmpty();
    }

    @Test
    public void testGlobalDecorationGeneratingInfiniteRecursion() throws Exception {
        // This case reproduce a situation where a decorator have a dependency on a heap object
        // If this dependency is resolved at the time of decorator creation, that induce a StackOverFlowError:
        // If the dependency is not yet created, the heap will instantiate it with its heaplet, and then will try
        // decorate it, but as the decorator is not yet finished (because it needs the dependency to be resolved),
        // the heap creates a new decorator instance, that will again try to resolve the heap object, ...

        // This problem is solved by delaying the heap object resolution (using LazyReference<T> for example)
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-global-decorator-recursion.json"));
    }

    @Test
    public void testGlobalDecoratorWithIncompatibleDecorators() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-global-decorations.json"));

        Book book = heap.get("book", Book.class);
        assertThat(book.getTitle()).isEqualTo("ABCD OpenIG 12345");
    }

    @Test
    public void testDecoratorsAreNotDecoratedThemselves() throws Exception {
        HeapImpl heap = buildDefaultHeap();
        heap.init(asJson("heap-decorators-are-not-decoratable.json"));

        Decorator decorator = heap.get("deco", Decorator.class);
        assertThat(decorator).isInstanceOf(DecoratorDecorator.class);
    }

    @Test
    public void testGlobalDecoratorAreInherited() throws Exception {
        HeapImpl parent = buildDefaultHeap();
        parent.init(asJson("heap-global-decorations-parent.json"));

        HeapImpl child = new HeapImpl(parent);
        child.init(asJson("heap-global-decorations-child.json"));

        Book book = child.get("book", Book.class);
        assertThat(book.getTitle()).isEqualTo("ABCD OpenIG 12345");
    }

    @Test
    public void shouldSupportObjectOverridingUntilInitialized2() throws Exception {
        HeapImpl heap = buildDefaultHeap();

        // Add 2 'org.forgerock.openig.heap.HeapObject' object declarations
        heap.addDefaultDeclaration(json(object(field("name", "Welcome"),
                                               field("type", "org.forgerock.openig.heap.HeapObject"))));

        heap.init(json(object(field("heap", array(json(object(field("name", "Welcome"),
                                                              field("type", "org.forgerock.openig.heap.HeapObject"),
                                                              field("config", object(field("message", "Hello"))))))))));
        // Verify this is the 2nd object in the heap
        assertThat(heap.get("Welcome", HeapObject.class).message).isEqualTo("Hello");
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void shouldFailForDuplicatedObjects() throws Exception {
        HeapImpl heap = buildDefaultHeap();

        Object declaration = object(field("name", "Welcome"), field("type", "WelcomeHandler"));
        heap.init(json(object(field("heap", array(declaration,
                                                  declaration)))));
    }

    @Test(expectedExceptions = HeapException.class)
    public void shouldFailForInlineDeclarationWithSameName() throws Exception {
        HeapImpl heap = buildDefaultHeap();

        // This is an inline declaration with a name
        Object architect = object(field("name", "Duplicated"),
                                  field("type", "org.forgerock.openig.heap.domain.Architect"),
                                  field("config", object(field("name", "Hello"))));
        Object matrix = object(field("name", "Duplicated"),
                               field("type", "org.forgerock.openig.heap.domain.Matrix"),
                               field("config", object(field("architect-ref", architect))));

        heap.init(json(object(field("heap", array(matrix)))));
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void shouldFailForDuplicatedObjectsWithInlineDeclaration() throws Exception {
        HeapImpl heap = buildDefaultHeap();

        // This is an inline declaration with a name
        Object matrix = object(field("name", "Duplicated"),
                               field("type", "org.forgerock.openig.heap.domain.Matrix"));
        Object architect = object(field("name", "Duplicated"),
                                  field("type", "org.forgerock.openig.heap.domain.Architect"),
                                  field("config", object(field("name", "Hello"))));
        Object matrix2 = object(field("name", "Matrix"),
                               field("type", "org.forgerock.openig.heap.domain.Matrix"),
                               field("config", object(field("architect-ref", architect))));

        heap.init(json(object(field("heap", array(matrix, matrix2)))));
    }

    @Test
    public void shouldGetAllSucceed() throws Exception {
        final HeapImpl grandParent = new HeapImpl();
        final Book book = new Book("OpenIG-101");
        grandParent.put("book", book);

        final HeapImpl parent = new HeapImpl(grandParent);
        final Book book1 = new Book("OpenIG-Reference");
        final Book book2 = new Book("OpenIG-Guide");
        parent.put("book1", book1);
        parent.put("architect", new Architect());

        final HeapImpl child = new HeapImpl(parent);
        child.put("book2", book2);

        final List<Book> books = child.getAll(Book.class);
        assertThat(books).hasSize(3)
                         .contains(book, book1, book2);
    }

    @Test
    public void shouldGetAllNotOverrideChildItems() throws Exception {
        final Book book = new Book("OpenIG-Reference");
        book.setEdition("Basic ForgeRock AS");

        final HeapImpl parent = new HeapImpl();
        parent.put("architect", new Architect());
        parent.put("myBook", book);

        final HeapImpl child = new HeapImpl(parent);
        book.setEdition("Collector");
        child.put("myBook", book);

        final List<Book> books = child.getAll(Book.class);
        assertThat(books).hasSize(1).containsOnly(book);
        assertThat(books.get(0).getEdition()).isEqualTo("Collector");
    }

    @Test
    public void shouldGetAllReturnsEmptyListIfNoItem() throws Exception {

        final HeapImpl parent = new HeapImpl();
        parent.put("book1", new Book("OpenIG-Reference"));

        final HeapImpl child = new HeapImpl(parent);
        parent.put("book2", new Book("OpenIG-Guide"));

        assertThat(child.getAll(Architect.class)).isEmpty();
    }

    @Test
    public void shouldUseOnlyLocalHeapForInlineDeclaration() throws Exception {
        HeapImpl parentHeap = buildDefaultHeap();
        parentHeap.init(asJson("heap-object-creation.json"));

        HeapImpl childHeap = new HeapImpl(parentHeap);
        childHeap.init(asJson("heap-with-no-objects.json"));

        JsonValue config = json(object(field("type", "org.forgerock.openig.heap.HeapObject"),
                                       field("name", "heap-object"),
                                       field("config", object(field("message", "child")))));
        HeapObject resolved = childHeap.resolve(config, HeapObject.class);
        assertThat(resolved.message).isEqualTo("child");
    }

    @Test
    public void shouldResolvePropertiesFromParent() throws Exception {
        HeapImpl parentHeap = buildDefaultHeap();
        parentHeap.init(json(object(field("properties", object(field("name", "parent"))))));

        HeapImpl childHeap = new HeapImpl(parentHeap);
        childHeap.init(json(object(field("properties", object(field("name", "child"))))));

        JsonValue config = json(object(field("type", "org.forgerock.openig.heap.HeapObject"),
                                       field("name", "heap-object"),
                                       field("config", object(field("message", "${name}")))));
        assertThat(childHeap.resolve(config, HeapObject.class).message).isEqualTo("child");
        assertThat(parentHeap.resolve(config, HeapObject.class).message).isEqualTo("parent");
    }

    private JsonValue asJson(final String resourceName) throws Exception {
        return readJson(getClass().getResource(resourceName));
    }

    private static class BookDecorator implements Decorator {

        @Override
        public boolean accepts(final Class<?> type) {
            return Book.class.isAssignableFrom(type);
        }

        @Override
        public Object decorate(final Object delegate, final JsonValue decoratorConfig, final Context context)
                throws HeapException {
            return new DecoratedBook((Book) delegate, decoratorConfig, context);
        }

    }

    private static class DecoratedBook extends Book {
        public final Book delegate;
        public final JsonValue decoration;
        public final Context context;

        public DecoratedBook(final Book delegate,
                             final JsonValue decoration,
                             final Context context) {

            super("no name");
            this.delegate = delegate;
            this.decoration = decoration;
            this.context = context;
        }
    }

}
