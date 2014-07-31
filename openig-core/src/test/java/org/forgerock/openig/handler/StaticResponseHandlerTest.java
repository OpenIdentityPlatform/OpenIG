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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.util.StringUtil.asString;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class StaticResponseHandlerTest {

    @Test
    public void shouldSetStatusReasonAndHeaders() throws Exception {
        final StaticResponseHandler handler = new StaticResponseHandler(302, "Found");
        handler.addHeader("Location", new Expression("http://www.example.com/"));
        final Exchange exchange = new Exchange();
        handler.handle(exchange);
        assertThat(exchange.response.getStatus()).isEqualTo(302);
        assertThat(exchange.response.getReason()).isEqualTo("Found");
        assertThat(exchange.response.getHeaders().getFirst("Location")).isEqualTo("http://www.example.com/");
    }

    @Test
    public void shouldEvaluateTheEntityExpressionContent() throws Exception {
        final StaticResponseHandler handler =
                new StaticResponseHandler(
                        200,
                        null,
                        null,
                        new Expression(
                        "<a href='/login?goto=${urlEncode(exchange.goto)}'>GOTO</a>"));
        final Exchange exchange = new Exchange();
        exchange.put("goto", "http://goto.url");
        handler.handle(exchange);
        assertThat(exchange.response.getStatus()).isEqualTo(200);
        assertThat(exchange.response.getReason()).isEqualTo("OK");
        assertThat(asString(exchange.response.getEntity())).isEqualTo(
                "<a href='/login?goto=http%3A%2F%2Fgoto.url'>GOTO</a>");
    }
}
