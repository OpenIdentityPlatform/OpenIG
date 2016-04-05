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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.openig.filter.RequestCopyFilter.requestCopyFilter;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URISyntaxException;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RequestCopyFilterTest {

    private static final String REQUEST_URI = "https://www.example.com/rockstar?who=gandalf";
    private static final String MODIFIED_REQUEST_URI = "https://www.example.com/rockstar?who=gandalf&bridge=balrog";
    private static final String REQUEST_ENTITY = "I am a servant of the Secret Fire, wielder of the Flame of Anor.";

    private Context context;
    private TerminalHandler handler;
    private Request original;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);

        context = new RootContext();
        handler = new TerminalHandler();
        original = new Request();
        original.setMethod("GET")
                .setUri(REQUEST_URI)
                .setVersion("HTTP/1.1")
                .setEntity(REQUEST_ENTITY);
        original.getHeaders().add("Username", "gandalf");
        original.getHeaders().add("Password", "mithrandir");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailWithNullRequest() {
        requestCopyFilter().filter(context, null, handler);
    }

    @Test
    public void shouldCopyOriginalRequest() throws Exception {

        requestCopyFilter().filter(context, original, handler);

        assertThat(handler.request).isNotSameAs(original);

        // The copy request is modified by the handler
        assertThat(handler.request.getUri().toString()).isNotEqualTo(original.getUri().toString())
            .isEqualTo(MODIFIED_REQUEST_URI);
        assertThat(handler.request.getForm().toQueryString()).isNotEqualTo(original.getForm().toQueryString())
            .isEqualTo("who=gandalf&bridge=balrog");

        // Original is not modified
        assertThat(original.getUri().toString()).isEqualTo(REQUEST_URI);
        assertThat(original.getForm().toQueryString()).isEqualTo("who=gandalf");
        assertThat(original.getEntity().getString()).isEqualTo(REQUEST_ENTITY);

        // The other fields were copied from original
        assertThat(handler.request.getMethod()).isEqualTo(original.getMethod());
        assertThat(handler.request.getVersion()).isEqualTo(original.getVersion());
        assertThat(handler.request.getHeaders().keySet()).containsAll(original.getHeaders().keySet());
        // At this point the entity stream of the copy is closed:
        assertThat(handler.request.getEntity().getString()).isEmpty();
    }

    private static class TerminalHandler implements Handler {
        Request request;

        @Override
        public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
            this.request = request;
            try {
                request.setUri(MODIFIED_REQUEST_URI);
            } catch (URISyntaxException e) {
                // Should not happen.
            }
            request.setEntity("You shall not pass!");
            return newResultPromise(new Response(OK));
        }
    }
}
