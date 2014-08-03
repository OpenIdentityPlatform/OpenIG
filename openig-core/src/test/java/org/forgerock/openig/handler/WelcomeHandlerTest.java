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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;

import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class WelcomeHandlerTest {

    @Test
    public void getWelcomePage() throws Exception {
        final WelcomeHandler handler = new WelcomeHandler();
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setMethod("GET");
        exchange.request.setUri("http://example.com/");
        handler.handle(exchange);
        assertThat(exchange.response.getStatus()).isEqualTo(200);
        assertThat(exchange.response.getReason()).isEqualTo("OK");
        assertThat(exchange.response.getHeaders()).containsEntry("Content-Type",
                Arrays.asList("text/html"));
        assertThat(exchange.response.getEntity().getRawInputStream().available()).isGreaterThan(0);
        exchange.response.close();
    }
}
