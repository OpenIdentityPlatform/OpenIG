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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.doc;

import static org.assertj.core.api.Assertions.*;

import org.forgerock.http.protocol.Request;
import org.forgerock.openig.filter.Chain;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.handler.StaticResponseHandler;
import org.forgerock.openig.http.Adapters;
import org.forgerock.openig.http.Exchange;
import org.testng.annotations.Test;

import java.io.IOException;

@SuppressWarnings("javadoc")
public class SampleFilterTest {

    @Test
    public void onRequest() throws IOException, HandlerException {
        SampleFilter filter = new SampleFilter();
        filter.name  = "X-Greeting";
        filter.value = "Hello world";

        Exchange exchange = new Exchange();
        exchange.request = new Request();

        final StaticResponseHandler handler = new StaticResponseHandler(200, "OK");
        Chain chain = new Chain(Adapters.asHandler(handler));
        chain.getFilters().add(filter);

        chain.handle(exchange);

        assertThat(exchange.request.getHeaders().get(filter.name))
                .containsOnly("Hello world");
        assertThat(exchange.response.getHeaders().get(filter.name))
                .containsOnly("Hello world");
    }
}
