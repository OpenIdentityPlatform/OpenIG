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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2012 ForgeRock AS. All rights reserved.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.StaticResponseHandler;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.MessageType;
import org.forgerock.openig.http.Request;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class HeaderFilterTest {
    private Exchange exchange;

    @BeforeMethod
    public void beforeMethod() {
        exchange = new Exchange();
        exchange.request = new Request();
    }

    @Test
    public void testAddHeaderToTheResponse() throws Exception {
        HeaderFilter filter = new HeaderFilter();
        filter.messageType = MessageType.RESPONSE;
        filter.remove.add("Location");
        filter.add.add("Location", "http://newtest.com:321${exchange.request.uri.path}");

        exchange.request.method = "DELETE";
        exchange.request.uri = new URI("http://test.com:123/path/to/resource.html");
        Chain chain = new Chain();
        chain.filters.add(filter);
        StaticResponseHandler handler = new StaticResponseHandler();
        handler.status = 200;
        chain.handler = handler;
        chain.handle(exchange);

        assertThat(exchange.response.headers.get("Location"))
                .containsOnly("http://newtest.com:321/path/to/resource.html");
    }

    @Test
    public void testRemoveHeaderFromTheResponse() throws Exception {
        HeaderFilter filter = new HeaderFilter();
        filter.messageType = MessageType.RESPONSE;
        filter.remove.add("Location");

        // Prepare a static response handler that provision a response header
        StaticResponseHandler handler = new StaticResponseHandler();
        handler.headers.putSingle("Location", new Expression("http://openig.forgerock.com"));
        handler.status = 200;

        // Execute the filter
        filter.filter(exchange, handler);

        // Verify that the response header has been removed
        assertThat(exchange.response.headers.get("Location")).isNull();
    }
}
