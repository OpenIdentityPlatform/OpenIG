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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.openam;

import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Status.BAD_REQUEST;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.http.protocol.Status.UNAUTHORIZED;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SsoTokenFilterTest {

    static final private URI OPENAM_URI = URI.create("http://www.example.com:8090/openam/");
    static final private String VALID_TOKEN = "AAAwns...*";
    static final private Object AUTHENTICATION_SUCCEEDED = object(field("tokenId", VALID_TOKEN),
                                                                  field("successUrl", "/openam/console"));
    private static final String DEFAULT_HEADER_NAME = "iPlanetDirectoryPro";

    private AttributesContext attributesContext;
    private Request request;
    private Response authenticated;
    private Response unauthorized;

    @Mock
    static Handler authenticate, next;

    @Mock
    static Logger logger;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        attributesContext = new AttributesContext(new RootContext());
        attributesContext.getAttributes().put("password", "hifalutin");

        request = new Request();
        unauthorized = new Response();
        unauthorized.setStatus(UNAUTHORIZED).setEntity(json(object(field("code", 401),
                                                                   field("reason", "Unauthorized"),
                                                                   field("message", "Access denied"))));
        authenticated = new Response();
        authenticated.setStatus(OK).setEntity(AUTHENTICATION_SUCCEEDED);
    }

    @DataProvider
    private static Object[][] nullRequiredParameters() {
        return new Object[][] {
            { null, OPENAM_URI },
            { next, null } };
    }

    @Test(dataProvider = "nullRequiredParameters", expectedExceptions = NullPointerException.class)
    public void shouldFailToCreateFilterWithNullRequiredParameters(final Handler handler,
                                                                   final URI openAmUri) throws Exception {
        new SsoTokenFilter(handler,
                           openAmUri,
                           "/",
                           DEFAULT_HEADER_NAME,
                           Expression.valueOf("bjensen", String.class),
                           Expression.valueOf("${attributes.password}", String.class),
                           logger);
    }

    @Test
    public void shouldCreateRequestForSSOToken() throws Exception  {
        final SsoTokenFilter filter =
                new SsoTokenFilter(authenticate,
                                   OPENAM_URI,
                                   "/myrealm/sub",
                                   DEFAULT_HEADER_NAME,
                                   Expression.valueOf("bjensen", String.class),
                                   Expression.valueOf("${attributes.password}", String.class),
                                   logger);
        final Request request = filter.authenticationRequest(bindings(attributesContext, null));
        assertThat(request.getHeaders().get("X-OpenAM-Username").getFirstValue()).isEqualTo("bjensen");
        assertThat(request.getHeaders().get("X-OpenAM-Password").getFirstValue()).isEqualTo("hifalutin");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getUri().toASCIIString()).isEqualTo(OPENAM_URI + "json/myrealm/sub/authenticate");
    }

    @DataProvider
    private static Object[][] ssoTokenHeaderName() {
        return new Object[][] {
            { null },
            { DEFAULT_HEADER_NAME },
            { "iForgeSession" } };
    }

    @Test(dataProvider = "ssoTokenHeaderName")
    public void shouldRequestForSSOTokenWhenNone(final String givenSsoTokenHeaderName) throws Exception {
        // Given
        when(authenticate.handle(any(Context.class), any(Request.class)))
                                .thenReturn(newResponsePromise(authenticated));

        when(next.handle(attributesContext, request)).thenReturn(newResponsePromise(new Response(OK)));

        // When
        buildSsoTokenFilter(givenSsoTokenHeaderName).filter(attributesContext, request, next);

        // Then
        verify(authenticate).handle(any(Context.class), any(Request.class));
        verify(next).handle(attributesContext, request);
        assertThat(request.getHeaders().get(givenSsoTokenHeaderName != null
                                            ? givenSsoTokenHeaderName
                                            : DEFAULT_HEADER_NAME).getFirstValue()).isEqualTo(VALID_TOKEN);
    }

    @Test
    public void shouldRequestForNewSSOTokenOnlyOnceWhenFirstRequestFailed() throws Exception {
        // Given
        when(next.handle(any(Context.class), any(Request.class))).thenReturn(newResponsePromise(unauthorized));

        when(authenticate.handle(any(Context.class), any(Request.class)))
                                .thenReturn(newResponsePromise(authenticated));

        // When
        final Response finalResponse = buildSsoTokenFilter().filter(attributesContext,
                                                                    request,
                                                                    next).get();

        // Then
        verify(authenticate, times(2)).handle(any(Context.class), any(Request.class));
        verify(next, times(2)).handle(attributesContext, request);
        assertThat(request.getHeaders().containsKey(DEFAULT_HEADER_NAME)).isTrue();
        assertThat(finalResponse).isSameAs(unauthorized);
    }

    @Test
    public void shouldRequestForSSOTokenFails() throws Exception {
        // Given
        final Response badRequestResponse = new Response();
        badRequestResponse.setStatus(BAD_REQUEST);

        when(authenticate.handle(any(Context.class), any(Request.class)))
                .thenReturn(newResponsePromise(badRequestResponse));

        final SsoTokenFilter ssoTokenFilter = buildSsoTokenFilter();

        // When
        final Response finalResponse = ssoTokenFilter.filter(attributesContext, request, next).get();

        // Then
        verifyZeroInteractions(next);
        verify(authenticate).handle(any(Context.class), any(Request.class));
        assertThat(request.getHeaders().containsKey(DEFAULT_HEADER_NAME)).isFalse();
        assertThat(finalResponse.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(finalResponse.getEntity().getString()).isEmpty();
        verify(logger).error("Unable to retrieve SSO Token");
    }

    @Test(timeOut = 1000)
    public void shouldOnlyAuthenticateOnceOnMultiThreadingMode() throws Exception {
        // Given
        when(authenticate.handle(any(Context.class), any(Request.class))).thenReturn(newResponsePromise(authenticated));
        when(next.handle(attributesContext, request)).thenReturn(newResponsePromise(new Response(OK)));

        final SsoTokenFilter ssoTokenFilter = buildSsoTokenFilter();
        final Runnable action = new Runnable() {
            @Override
            public void run() {
                ssoTokenFilter.filter(attributesContext, request, next);
            }
        };
        final int taskNumber = 10;
        final CountDownLatch started = new CountDownLatch(taskNumber);
        final CountDownLatch finished = new CountDownLatch(taskNumber);
        final ExecutorService executorService = newFixedThreadPool(taskNumber);

        // When
        for (int i = 0; i < taskNumber; i++) {
            executorService.execute(new Worker(action, started, finished));
        }

        finished.await();

        // Then
        verify(authenticate).handle(any(Context.class), any(Request.class));
        verify(next, times(taskNumber)).handle(attributesContext, request);
        shutdownExecutor(executorService);
    }

    @SuppressWarnings("unchecked")
    @Test(timeOut = 1000)
    public void shouldUpdateTokenOnMultiThreadingMode() throws Exception {
        // Given
        when(authenticate.handle(any(Context.class), any(Request.class))).thenReturn(newResponsePromise(authenticated));
        when(next.handle(attributesContext, request)).thenReturn(newResponsePromise(new Response(OK)),
                                                                 newResponsePromise(unauthorized),
                                                                 newResponsePromise(new Response(OK)));

        final SsoTokenFilter ssoTokenFilter = buildSsoTokenFilter();

        final Runnable action = new Runnable() {
            @Override
            public void run() {
                ssoTokenFilter.filter(attributesContext, request, next);
            }
        };
        final int taskNumber = 10;
        final CountDownLatch started = new CountDownLatch(taskNumber);
        final CountDownLatch finished = new CountDownLatch(taskNumber);
        final ExecutorService executorService = newFixedThreadPool(taskNumber);

        // When
        // First call
        ssoTokenFilter.filter(attributesContext, request, next).get();
        // before workers...
        for (int i = 0; i < taskNumber; i++) {
            executorService.execute(new Worker(action, started, finished));
        }

        finished.await();

        // Then
        // Authenticate is called a first time to generate a token + one more time to update the token
        verify(authenticate, times(2)).handle(any(Context.class), any(Request.class));
        // Next is called a first time before the workers, plus (x + 1) times by the workers (task_number + one
        // for managing the unauthorized response).
        verify(next, times(1 + taskNumber + 1)).handle(attributesContext, request);
        shutdownExecutor(executorService);
    }

    class Worker implements Runnable {
        private Runnable action;
        private final CountDownLatch started;
        private final CountDownLatch finished;

        public Worker(Runnable action, CountDownLatch started, CountDownLatch finished) {
            this.action = action;
            this.started = started;
            this.finished = finished;
        }

        @Override
        public void run() {
            started.countDown();
            try {
                started.await();
                action.run();
                finished.countDown();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void shutdownExecutor(final ExecutorService executorService) {
        try {
            executorService.shutdown();
        } finally {
            executorService.shutdownNow();
        }
    }

    private static SsoTokenFilter buildSsoTokenFilter() throws Exception {
        return buildSsoTokenFilter(null);
    }

    private static SsoTokenFilter buildSsoTokenFilter(final String headerName) throws Exception {
        return new SsoTokenFilter(authenticate,
                                  OPENAM_URI,
                                  null,
                                  headerName,
                                  Expression.valueOf("bjensen", String.class),
                                  Expression.valueOf("${attributes.password}", String.class),
                                  logger);
    }
}
