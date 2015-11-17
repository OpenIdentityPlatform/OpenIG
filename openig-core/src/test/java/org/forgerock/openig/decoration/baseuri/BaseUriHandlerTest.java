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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.decoration.baseuri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URISyntaxException;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BaseUriHandlerTest {

    private static final String REQUEST_URI = "http://www.forgerock.org/key_path";

    @Mock
    private Handler delegate;

    @Mock
    private Logger logger;

    private Context context;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        context = new RootContext();
    }

    @Test
    public void shouldRebaseUri() throws Exception {
        final BaseUriHandler handler = new BaseUriHandler(delegate,
                                                          Expression.valueOf("http://www.example.com:443",
                                                                  String.class),
                                                          logger);

        final Request request = createRequest();
        handler.handle(context, request);

        verify(delegate).handle(context, request);

        assertThat(request.getUri().toString()).isEqualTo("http://www.example.com:443/key_path");
        verifyZeroInteractions(logger);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailWithNullExpression() throws Exception {
        final BaseUriHandler handler = new BaseUriHandler(delegate,
                                                          null,
                                                          logger);

        final Request request = createRequest();
        handler.handle(context, request);
        verify(logger).error(anyString());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void shouldFailWhenRebasingFail() throws Exception {
        final BaseUriHandler handler = new BaseUriHandler(delegate,
                                                          Expression.valueOf("http://<<servername>>:8080",
                                                                  String.class),
                                                          logger);

        final Request request = createRequest();
        handler.handle(context, request);
        verify(logger).error(anyString());
    }

    @Test
    public void shouldReturnErrorResponseDueToUnresolvableExpression() throws Exception {
        final BaseUriHandler handler = new BaseUriHandler(delegate,
                                                          Expression.valueOf("${EXPRESSION_ERROR}",
                                                                  String.class),
                                                          logger);

        final Request request = createRequest();
        final Response response = handler.handle(context, request).get();
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).isEmpty();
        verify(logger).error(anyString());
    }

    private Request createRequest() throws URISyntaxException {
        Request request = new Request();
        request.setUri(REQUEST_URI);
        return request;
    }
}
