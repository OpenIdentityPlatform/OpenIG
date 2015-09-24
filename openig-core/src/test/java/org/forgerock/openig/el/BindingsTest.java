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
 * Copyright 2015 ForgeRock AS.
 *
 */

package org.forgerock.openig.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.forgerock.openig.el.Bindings.bindings;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.http.Exchange;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BindingsTest {

    @Test
    public void shouldAddEntryInMap() throws Exception {
        assertThat(bindings().bind("a", "b").asMap()).containsOnly(entry("a", "b"));
    }

    @Test
    public void shouldAddNullEntryInMap() throws Exception {
        assertThat(bindings().bind("a", null).asMap()).containsOnly(entry("a", null));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldRejectNullName() throws Exception {
        bindings().bind(null, "b");
    }

    @Test
    public void shouldCreateSingletonBinding() throws Exception {
        assertThat(bindings("a", "b").asMap()).containsOnly(entry("a", "b"));
    }

    @Test
    public void shouldBindExchangeAndRequest() throws Exception {
        assertThat(bindings(new Exchange(), new Request()).asMap())
                .containsKeys("exchange", "context", "request")
                .hasSize(3);
    }

    @Test
    public void shouldBindExchangeRequestAndResponse() throws Exception {
        assertThat(bindings(new Exchange(), new Request(), new Response()).asMap())
                .containsKeys("exchange", "context", "request", "response")
                .hasSize(4);
    }

    @Test
    public void shouldBindContextAndRequest() throws Exception {
        assertThat(bindings(new RootContext(), new Request()).asMap())
                .containsKeys("context", "request")
                .hasSize(2);
    }

    @Test
    public void shouldBindContextRequestAndResponse() throws Exception {
        assertThat(bindings(new RootContext(), new Request(), new Response()).asMap())
                .containsKeys("context", "request", "response")
                .hasSize(3);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void shouldFailWhenModifyingBindingAsMap() throws Exception {
        bindings().bind("a", "b")
                  .asMap()
                  .put("a", "c");
    }
}
