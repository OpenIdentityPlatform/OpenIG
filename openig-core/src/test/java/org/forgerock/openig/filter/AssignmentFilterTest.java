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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.StaticResponseHandler;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AssignmentFilterTest {

    final static String VALUE = "SET";

    @DataProvider
    private Object[][] validConfigurations() {
        return new Object[][] {
            { json(object(field("onRequest", array(
                    object(
                        field("target", "${contexts.attributes.attributes.target}"),
                        field("value", VALUE)))))) },
            { json(object(field("onRequest", array(
                    object(
                        field("target", "${contexts.attributes.attributes.target}"),
                        field("value", VALUE),
                        field("condition", "${1==1}")))))) },
            { json(object(field("onResponse", array(
                    object(
                        field("target", "${contexts.attributes.attributes.target}"),
                        field("value", VALUE)))))) },
            { json(object(field("onResponse", array(
                    object(
                        field("target", "${contexts.attributes.attributes.target}"),
                        field("value", VALUE),
                        field("condition", "${1==1}")))))) } };
    }

    @DataProvider
    private Object[][] invalidConfigurations() {
        return new Object[][] {
            /* Missing target. */
            { json(object(
                    field("onRequest", array(object(
                            field("value", VALUE)))))) },
            /* Missing target (bis). */
            { json(object(
                    field("onResponse", array(object(
                            field("value", VALUE),
                            field("condition", "${1==1}")))))) } };
    }

    @Test(dataProvider = "invalidConfigurations", expectedExceptions = JsonValueException.class)
    public void shouldFailToCreateHeapletWhenRequiredAttributeIsMissing(final JsonValue config) throws Exception {
        buildAssignmentFilter(config);
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws HeapException, Exception {
        final AssignmentFilter filter = buildAssignmentFilter(config);
        AttributesContext context = new AttributesContext(new RootContext());
        final StaticResponseHandler handler = new StaticResponseHandler(Status.OK);
        chainOf(handler, filter).handle(context, null).get();
        assertThat(context.getAttributes().get("target")).isEqualTo(VALUE);
    }

    @Test
    public void shouldSucceedToUnsetVar() throws Exception {
        final AssignmentFilter filter = new AssignmentFilter();
        final Expression<String> target = Expression.valueOf("${contexts.attributes.attributes.target}", String.class);
        filter.addRequestBinding(target, null);

        AttributesContext context = new AttributesContext(new RootContext());
        context.getAttributes().put("target", "UNSET");
        final StaticResponseHandler handler = new StaticResponseHandler(Status.OK);
        final Handler chain = chainOf(handler, singletonList((Filter) filter));
        Bindings bindings = bindings(context, null);
        assertThat(target.eval(bindings)).isEqualTo("UNSET");

        chain.handle(context, null).get();
        assertThat(target.eval(bindings)).isNull();
    }

    @Test
    public void onRequest() throws Exception {
        AssignmentFilter filter = new AssignmentFilter();
        final Expression<String> target = Expression.valueOf("${contexts.attributes.attributes.newAttr}", String.class);
        filter.addRequestBinding(target,
                                 Expression.valueOf("${request.method}", String.class));

        AttributesContext context = new AttributesContext(new RootContext());
        Request request = new Request();
        request.setMethod("DELETE");
        final StaticResponseHandler handler = new StaticResponseHandler(Status.OK);
        Chain chain = new Chain(handler, singletonList((Filter) filter));
        chain.handle(context, request).get();
        assertThat(context.getAttributes().get("newAttr")).isEqualTo("DELETE");
    }

    @Test
    public void shouldChangeUriOnRequest() throws Exception {
        AssignmentFilter filter = new AssignmentFilter();
        filter.addRequestBinding(Expression.valueOf("${request.uri}", String.class),
                                 Expression.valueOf("www.forgerock.com", String.class));

        Exchange exchange = new Exchange();
        Request request = new Request();
        request.setUri("www.example.com");

        Chain chain = new Chain(new StaticResponseHandler(Status.OK), singletonList((Filter) filter));

        chain.handle(exchange, request).get();
        assertThat(request.getUri().toString()).isEqualTo("www.forgerock.com");
    }

    @Test
    public void onResponse() throws Exception {
        AssignmentFilter filter = new AssignmentFilter();
        final Expression<String> target = Expression.valueOf("${contexts.attributes.attributes.newAttr}", String.class);
        filter.addResponseBinding(target,
                                  Expression.valueOf("${response.status.code}", Integer.class));

        AttributesContext context = new AttributesContext(new RootContext());
        Chain chain = new Chain(new StaticResponseHandler(Status.OK), singletonList((Filter) filter));
        chain.handle(context, new Request()).get();
        assertThat(context.getAttributes().get("newAttr")).isEqualTo(200);
    }

    private AssignmentFilter buildAssignmentFilter(final JsonValue config) throws Exception {
        final AssignmentFilter.Heaplet heaplet = new AssignmentFilter.Heaplet();
        return (AssignmentFilter) heaplet.create(Name.of("myAssignmentFilter"),
                                                 config,
                                                 buildDefaultHeap());
    }
}
