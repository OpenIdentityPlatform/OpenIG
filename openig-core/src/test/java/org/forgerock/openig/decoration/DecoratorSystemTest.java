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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.decoration;

import static java.lang.String.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.http.util.Json.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.HeapImplTest;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DecoratorSystemTest {

    @Test
    public void shouldDecorateObjectDeclaration() throws Exception {
        HeapImpl heap = HeapImplTest.buildDefaultHeap();
        heap.put("make-title", new MakeTitleDecorator());

        JsonValue config = asJson("decorate-object-declaration.json");
        heap.init(config);

        assertThatResponseEntityIsEqualTo(heap.resolve(config.get("handler"), Handler.class), "<h1>Hello World</h1>");
    }

    @Test
    public void shouldDecorateAllObjectDeclarations() throws Exception {
        HeapImpl heap = HeapImplTest.buildDefaultHeap();
        heap.put("make-title", new MakeTitleDecorator());

        JsonValue config = asJson("decorate-all-object-declarations.json");
        heap.init(config);

        // Decorated twice (Chain + response handler)
        assertThatResponseEntityIsEqualTo(heap.resolve(config.get("handler"), Handler.class),
                                          "<h1><h1>Hello World</h1></h1>");
    }

    @Test
    public void shouldDecorateTopLevelReference() throws Exception {
        HeapImpl heap = HeapImplTest.buildDefaultHeap();
        heap.put("make-title", new MakeTitleDecorator());
        heap.init(asJson("decorate-top-level-reference.json"));

        // Top level handler is decorated
        assertThatResponseEntityIsEqualTo(heap.getHandler(),
                                          "<h1>Hello World</h1>");

        // ... not the underlying Chain object
        assertThatResponseEntityIsEqualTo(heap.get("Chain", Handler.class),
                                          "Hello World");
    }

    @Test
    public void shouldDecorateTopLevelInlineReference() throws Exception {
        HeapImpl heap = HeapImplTest.buildDefaultHeap();
        heap.put("make-title", new MakeTitleDecorator());
        JsonValue config = asJson("decorate-top-level-inline-reference.json");
        heap.init(config);


        // Top level handler is decorated
        assertThatResponseEntityIsEqualTo(heap.getHandler(),
                                          "<h1>Hello World</h1>");

        // ... not the underlying Chain object
        assertThatResponseEntityIsEqualTo(heap.get("Chain", Handler.class),
                                          "Hello World");
    }

    @Test
    public void shouldDecorateGetReferencesFromParentHeap() throws Exception {
        HeapImpl parent = HeapImplTest.buildDefaultHeap();
        parent.put("make-title", new MakeTitleDecorator());
        parent.init(asJson("decorate-reference-from-parent-heap-parent.json"));

        HeapImpl child = new HeapImpl(parent, Name.of("child"));
        child.init(asJson("decorate-reference-from-parent-heap-child-get-and-global.json"));

        // Get the object that is declared in the parent heap (from the child heap)
        // Assert it is decorated
        assertThatResponseEntityIsEqualTo(child.get("HelloWorld", Handler.class),
                                          "<h1>Hello World</h1>");

        // Get the object that is declared in the parent heap (from the parent - undecorated - heap)
        // Assert it is NOT decorated
        assertThatResponseEntityIsEqualTo(parent.get("HelloWorld", Handler.class),
                                          "Hello World");
    }

    @Test
    public void shouldDecorateResolvedReferencesFromParentHeap() throws Exception {
        HeapImpl parent = HeapImplTest.buildDefaultHeap();
        parent.put("make-title", new MakeTitleDecorator());
        parent.init(asJson("decorate-reference-from-parent-heap-parent.json"));

        HeapImpl child = new HeapImpl(parent, Name.of("child"));
        JsonValue config = asJson("decorate-reference-from-parent-heap-child-resolve-and-global.json");
        child.init(config);

        // Get the object that is declared in the parent heap (from the child heap)
        // Assert it is decorated (Chain + response handler)
        assertThatResponseEntityIsEqualTo(child.resolve(config.get("handler"), Handler.class),
                                          "<h1><h1>Hello World</h1></h1>");

        // Get the object that is declared in the parent heap (from the parent - undecorated - heap)
        // Assert it is NOT decorated
        assertThatResponseEntityIsEqualTo(parent.get("HelloWorld", Handler.class),
                                          "Hello World");
    }

    @Test
    public void shouldDecorateTopLevelGetReferencesFromParentHeap() throws Exception {
        HeapImpl parent = HeapImplTest.buildDefaultHeap();
        parent.put("make-title", new MakeTitleDecorator());
        parent.init(asJson("decorate-reference-from-parent-heap-parent.json"));

        HeapImpl child = new HeapImpl(parent, Name.of("child"));
        JsonValue config = asJson("decorate-top-level-reference-from-parent-heap-child.json");
        child.init(config);

        // Get the object that is declared in the parent heap (from the child heap)
        // Assert it is decorated
        assertThatResponseEntityIsEqualTo(child.getHandler(), "<h1>Hello World</h1>");

        // Get the object that is declared in the parent heap (from the parent - undecorated - heap)
        // Assert it is NOT decorated
        assertThatResponseEntityIsEqualTo(parent.get("HelloWorld", Handler.class), "Hello World");
    }

    @Test
    public void shouldDecorateReferenceWithInheritance() throws Exception {
        // Expects the following tree:
        // h1
        //  -> h2
        //      -> h3
        //  -> h4

        HeapImpl h1 = HeapImplTest.buildDefaultHeap();
        h1.put("make-title", new MakeTitleDecorator());
        h1.init(asJson("decorate-reference-with-decorator-inheritance-1.json"));

        HeapImpl h2 = new HeapImpl(h1, Name.of("h2"));
        h2.init(asJson("decorate-reference-with-decorator-inheritance-2.json"));

        HeapImpl h3 = new HeapImpl(h2, Name.of("h3"));
        h3.init(asJson("decorate-reference-with-decorator-inheritance-3.json"));

        HeapImpl h4 = new HeapImpl(h1, Name.of("h4"));
        h4.init(asJson("decorate-reference-with-decorator-inheritance-4.json"));

        // HelloWorld from h1 should be decorated with h1
        assertThatResponseEntityIsEqualTo(h1.get("HelloWorld", Handler.class),
                                          "<h1>Hello World</h1>");
        // HelloWorld from h2 should be decorated with h1 and h2
        assertThatResponseEntityIsEqualTo(h2.get("HelloWorld", Handler.class),
                                          "<h2><h1>Hello World</h1></h2>");
        // HelloWorld from h3 should be decorated with h1, h2 and h3
        assertThatResponseEntityIsEqualTo(h3.get("HelloWorld", Handler.class),
                                          "<h3><h2><h1>Hello World</h1></h2></h3>");
        // HelloWorld from h4 should be decorated with h1 and h4 (does not inherit from h2 or h3)
        assertThatResponseEntityIsEqualTo(h4.get("HelloWorld", Handler.class),
                                          "<h4><h1>Hello World</h1></h4>");
    }

    @Test
    public void shouldDecorateObjectWithInheritedGlobalDecorator() throws Exception {
        HeapImpl parent = HeapImplTest.buildDefaultHeap();
        parent.put("make-title", new MakeTitleDecorator());
        parent.init(asJson("decorate-object-with-inherited-global-decorator-parent.json"));

        HeapImpl child = new HeapImpl(parent, Name.of("child"));
        child.init(asJson("decorate-object-with-inherited-global-decorator-only.json"));

        // Get the child-declared handler and ensure it has the decorations declared in parent heap
        assertThatResponseEntityIsEqualTo(child.get("HelloWorld", Handler.class), "<h1>Hello World</h1>");
    }

    @Test
    public void shouldDecorateObjectWithInheritedAndLocalGlobalDecorator() throws Exception {
        HeapImpl parent = HeapImplTest.buildDefaultHeap();
        parent.put("make-title", new MakeTitleDecorator());
        parent.init(asJson("decorate-object-with-inherited-global-decorator-parent.json"));

        HeapImpl child = new HeapImpl(parent, Name.of("child"));
        child.init(asJson("decorate-object-with-inherited-and-local-global-decorator.json"));

        // Get the child-declared handler and ensure it has both the decorations declared in parent heap and in the
        // local heap
        // Applied in this order: parent (h1), local (h2)
        assertThatResponseEntityIsEqualTo(child.get("HelloWorld", Handler.class), "<h2><h1>Hello World</h1></h2>");
    }

    @Test
    public void shouldApplyDecoratorsInTheRightOrder() throws Exception {
        HeapImpl heap = HeapImplTest.buildDefaultHeap();
        heap.put("make-title", new MakeTitleDecorator());
        heap.init(asJson("decorate-top-level-reference-with-local-and-global-decorators.json"));

        // Assert decorators are applied in this order: inner/local (h1) > global (h2) > top-level ref (h3)
        assertThatResponseEntityIsEqualTo(heap.getHandler(), "<h3><h2><h1>Hello World</h1></h2></h3>");
    }

    @Test
    public void shouldApplyDecoratorsInTheRightOrder2() throws Exception {
        HeapImpl one = HeapImplTest.buildDefaultHeap();
        one.put("make-title", new MakeTitleDecorator());
        one.init(asJson("decorate-object-in-order-with-inheritance-parent.json"));

        HeapImpl two = new HeapImpl(one, Name.of("two"));
        two.init(asJson("decorate-object-in-order-with-inheritance.json"));

        // Assert decorators are applied in this order: global > inner/local > top-level ref
        assertThatResponseEntityIsEqualTo(two.get("HelloWorld", Handler.class),
                                          "<h2><h1><h0>Hello World</h0></h1></h2>");
    }

    private void assertThatResponseEntityIsEqualTo(final Handler handler, final String expected) throws Exception {
        Response response = handler.handle(new Exchange(), null).getOrThrow();
        assertThat(response.getEntity().getString()).isEqualTo(expected);
    }

    private JsonValue asJson(final String resourceName) throws Exception {
        final Reader reader = new InputStreamReader(getClass().getResourceAsStream(resourceName));
        return new JsonValue(readJson(reader));
    }

    private class MakeTitleDecorator implements Decorator {

        @Override
        public boolean accepts(final Class<?> type) {
            return Handler.class.isAssignableFrom(type);
        }

        @Override
        public Object decorate(final Object delegate, final JsonValue decoratorConfig, final Context context)
                throws HeapException {
            final String header = decoratorConfig.required().asString();
            final Handler handler = (Handler) delegate;
            return new Handler() {
                @Override
                public Promise<Response, ResponseException> handle(final org.forgerock.http.Context context,
                                                                   final Request request) {
                    return handler.handle(context, request)
                            .then(new Function<Response, Response, ResponseException>() {
                                @Override
                                public Response apply(final Response response) throws ResponseException {
                                    try {
                                        String content = format("<%s>%s</%s>",
                                                                header,
                                                                response.getEntity().getString(),
                                                                header);
                                        response.getEntity().setString(content);
                                        return response;
                                    } catch (IOException e) {
                                        throw new ResponseException("IOException", e);
                                    }
                                }
                            });
                }
            };
        }
    }

}
