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

import org.forgerock.http.Filter;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.StaticResponseHandler;
import org.forgerock.openig.http.Exchange;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AssignmentFilterTest {

    @Test
    public void onRequest() throws Exception {
        AssignmentFilter filter = new AssignmentFilter();
        final Expression<String> target = Expression.valueOf("${exchange.newAttr}", String.class);
        filter.addRequestBinding(target,
                                 Expression.valueOf("${exchange.request.method}", String.class));

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setMethod("DELETE");
        final StaticResponseHandler handler = new StaticResponseHandler(Status.OK);
        Chain chain = new Chain(handler, singletonList((Filter) filter));
        assertThat(target.eval(exchange)).isNull();
        chain.handle(exchange, exchange.request).get();
        assertThat(exchange.get("newAttr")).isEqualTo("DELETE");
    }

    @Test
    public void shouldChangeUriOnRequest() throws Exception {
        AssignmentFilter filter = new AssignmentFilter();
        filter.addRequestBinding(Expression.valueOf("${exchange.request.uri}", String.class),
                                 Expression.valueOf("www.forgerock.com", String.class));

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("www.example.com");

        Chain chain = new Chain(new StaticResponseHandler(Status.OK), singletonList((Filter) filter));

        chain.handle(exchange, exchange.request).get();
        assertThat(exchange.request.getUri().toString()).isEqualTo("www.forgerock.com");
    }

    @Test
    public void onResponse() throws Exception {
        AssignmentFilter filter = new AssignmentFilter();
        final Expression<String> target = Expression.valueOf("${exchange.newAttr}", String.class);
        filter.addResponseBinding(target,
                                  Expression.valueOf("${exchange.response.status.code}", Integer.class));

        Exchange exchange = new Exchange();
        Chain chain = new Chain(new StaticResponseHandler(Status.OK), singletonList((Filter) filter));
        assertThat(target.eval(exchange)).isNull();
        chain.handle(exchange, exchange.request).get();
        assertThat(exchange.get("newAttr")).isEqualTo(200);
    }
}
