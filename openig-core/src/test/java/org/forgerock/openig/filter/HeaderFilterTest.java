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
 * Copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.http.Filter;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.StaticResponseHandler;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.util.MessageType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class HeaderFilterTest {
    private Exchange exchange;

    @BeforeMethod
    public void beforeMethod() {
        exchange = new Exchange();
        exchange.setRequest(new Request());
    }

    @Test
    public void testAddHeaderToTheResponse() throws Exception {
        HeaderFilter filter = new HeaderFilter(MessageType.RESPONSE);
        filter.getRemovedHeaders().add("Location");
        filter.getAddedHeaders().add("Location", "http://newtest.com:321${exchange.request.uri.path}");

        exchange.getRequest().setMethod("DELETE");
        exchange.getRequest().setUri("http://test.com:123/path/to/resource.html");
        StaticResponseHandler handler = new StaticResponseHandler(Status.OK);
        Chain chain = new Chain(handler, singletonList((Filter) filter));
        Response response = chain.handle(exchange, exchange.getRequest()).get();

        assertThat(response.getHeaders().get("Location"))
                .containsOnly("http://newtest.com:321/path/to/resource.html");
    }

    @Test
    public void testRemoveHeaderFromTheResponse() throws Exception {
        HeaderFilter filter = new HeaderFilter(MessageType.RESPONSE);
        filter.getRemovedHeaders().add("Location");

        // Prepare a static response handler that provision a response header
        final StaticResponseHandler handler = new StaticResponseHandler(Status.OK);
        handler.addHeader("Location", Expression.valueOf("http://openig.forgerock.com", String.class));

        // Execute the filter
        Response response = filter.filter(exchange, null, handler).get();

        // Verify that the response header has been removed
        assertThat(response.getHeaders().get("Location")).isNull();
    }
}
