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
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

import org.testng.annotations.Test;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.http.Exchange;

public class StaticResponseHandlerTest {

    @Test
    public void redirect() throws ExpressionException, HandlerException, IOException {
        StaticResponseHandler handler = new StaticResponseHandler();
        handler.status = 302;
        handler.reason = "Found";
        handler.headers.add("Location", new Expression("http://www.example.com/"));
        Exchange exchange = new Exchange();
        handler.handle(exchange);
        assertThat(exchange.response.status).isEqualTo(302);
        assertThat(exchange.response.reason).isEqualTo("Found");
        assertThat(exchange.response.headers.getFirst("Location")).isEqualTo("http://www.example.com/");
    }
}
