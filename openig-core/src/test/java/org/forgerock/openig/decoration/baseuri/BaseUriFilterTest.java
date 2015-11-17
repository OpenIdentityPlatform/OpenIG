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

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BaseUriFilterTest {

    private static final String REQUEST_URI = "http://www.forgerock.org/key_path";

    private Filter delegate;

    @Mock
    private Logger logger;

    @Mock
    private Handler terminal;

    private Context context;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        delegate = new DelegateFilter();
        context = new RootContext();
    }

    @Test
    public void shouldRebaseUri() throws Exception {
        final BaseUriFilter baseUriFilter = new BaseUriFilter(delegate,
                                                              Expression.valueOf("http://www.example.com:443",
                                                                      String.class),
                                                              logger);

        final Request request = createRequest();
        baseUriFilter.filter(context, request, terminal);

        verify(terminal).handle(context, request);

        assertThat(request.getUri().toString()).isEqualTo("http://www.example.com:443/key_path");
        verifyZeroInteractions(logger);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailWithNullExpression() throws Exception {
        final BaseUriFilter baseUriFilter = new BaseUriFilter(delegate,
                                                              null,
                                                              logger);

        final Request request = createRequest();
        baseUriFilter.filter(context, request, terminal);
        verify(logger).error(anyString());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void shouldFailWhenRebasingFail() throws Exception {
        final BaseUriFilter baseUriFilter = new BaseUriFilter(delegate,
                                                              Expression.valueOf(
                                                                      "http://<<servername>>:8080",  String.class),
                                                              logger);

        final Request request = createRequest();
        baseUriFilter.filter(context, request, terminal);
        verify(logger).error(anyString());
    }

    @Test
    public void shouldReturnErrorResponseDueToUnresolvableExpression() throws Exception {
        final BaseUriFilter baseUriFilter = new BaseUriFilter(delegate,
                                                              Expression.valueOf("${EXPRESSION_ERROR}",
                                                                                 String.class),
                                                              logger);

        final Request request = createRequest();
        final Response response =  baseUriFilter.filter(context, request, terminal).get();
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).isEmpty();
        verify(logger).error(anyString());
    }

    private Request createRequest() throws URISyntaxException {
        Request request = new Request();
        request.setUri(REQUEST_URI);
        return request;
    }

    private static class DelegateFilter implements Filter {
        @Override
        public Promise<Response, NeverThrowsException> filter(final Context context,
                                                              final Request request,
                                                              final Handler next) {
            return next.handle(context, request);
        }
    }
}
