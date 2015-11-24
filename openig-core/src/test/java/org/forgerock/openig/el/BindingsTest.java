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
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
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
    public void shouldBindContextAndRequest() throws Exception {
        assertThat(bindings(new RootContext(), new Request()).asMap())
                .containsKeys("context", "request", "contexts")
                .hasSize(3);
    }

    @Test
    public void shouldBindContextAndRequestAndAttributes() {
        final AttributesContext attributesContext = new AttributesContext(new RootContext());
        final Bindings bindings = bindings(attributesContext, null);
        assertThat(bindings.asMap())
                .containsKeys("context", "request", "attributes", "contexts")
                .hasSize(4);
        assertThat(((Map<?, ?>) bindings.asMap().get("contexts")).get("attributes")).isEqualTo(attributesContext);
        assertThat(bindings.asMap().get("attributes")).isEqualTo(attributesContext.getAttributes());
    }

    @Test
    public void shouldBindContextAndRequestAndSession() {
        final SessionContext sessionContext = new SessionContext(new RootContext(), mock(Session.class));
        final Bindings bindings = bindings(sessionContext, null);
        assertThat(bindings.asMap())
                .containsKeys("context", "request", "session", "contexts")
                .hasSize(4);
        assertThat(((Map<?, ?>) bindings.asMap().get("contexts")).get("session")).isEqualTo(sessionContext);
        assertThat(bindings.asMap().get("session")).isEqualTo(sessionContext.getSession());
    }

    @Test
    public void shouldBindContextAndRequestAndSessionAndAttributes() {
        final Context context = new AttributesContext(new SessionContext(new RootContext(), mock(Session.class)));
        assertThat(bindings(context, null).asMap())
                .containsKeys("context", "request", "session", "contexts", "attributes")
                .hasSize(5);
    }

    @Test
    public void shouldBindContextRequestAndResponse() throws Exception {
        assertThat(bindings(new RootContext(), new Request(), new Response()).asMap())
                .containsKeys("context", "request", "response", "contexts")
                .hasSize(4);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void shouldFailWhenModifyingBindingAsMap() throws Exception {
        bindings().bind("a", "b")
                  .asMap()
                  .put("a", "c");
    }

    @Test
    public void shouldCopyBindings() throws Exception {
        Bindings source = bindings().bind("a", "b");
        Bindings target = bindings().bind("c", "d");
        assertThat(target.bind(source).asMap()).containsOnly(entry("a", "b"), entry("c", "d"));
    }
}
