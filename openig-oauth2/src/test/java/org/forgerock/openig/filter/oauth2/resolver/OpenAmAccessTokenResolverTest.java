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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.forgerock.openig.filter.oauth2.AccessToken;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Response;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OpenAmAccessTokenResolverTest {

    public static final String TOKEN = "ACCESS_TOKEN";

    @Mock
    private Handler client;

    @Mock
    private TimeService time;

    private OpenAmAccessTokenResolver resolver;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        resolver = new OpenAmAccessTokenResolver(client, time, "/oauth2/tokeninfo");

        doAnswer(new ExchangeAnswer() {
            @Override
            protected void answer(final Exchange exchange) {
                exchange.response = response(200, doubleQuote("{'expires_in':10, "
                                                                      + "'access_token':'ACCESS_TOKEN', "
                                                                      + "'scope': [ 'email', 'name' ]}"));
            }
        }).when(client).handle(any(Exchange.class));
    }

    private Response response(final int status, final String content) {
        return new Response().setStatus(status).setEntity(content);
    }

    @Test
    public void shouldProduceAnAccessToken() throws Exception {
        when(time.now()).thenReturn(0L);
        AccessToken token = resolver.resolve(TOKEN);
        assertThat(token.getToken()).isEqualTo(TOKEN);
        assertThat(token.getExpiresAt()).isEqualTo(10000L);
    }

    @Test(expectedExceptions = OAuth2TokenException.class)
    public void shouldThrowAnOAuthTokenExceptionCausedByAnError() throws Exception {

        //Given
        doAnswer(new ExchangeAnswer() {
            @Override
            protected void answer(final Exchange exchange) {
                exchange.response = response(400, doubleQuote("{'error':'ERROR'}"));
            }
        }).when(client).handle(any(Exchange.class));


        //When
        resolver.resolve(TOKEN);
    }

    @Test(expectedExceptions = OAuth2TokenException.class)
    public void shouldThrowAnOAuthTokenExceptionCausedByAnIOException() throws Exception {

        //Given
        doThrow(new IOException()).when(client).handle(any(Exchange.class));

        //When
        resolver.resolve(TOKEN);
    }

    // TODO Implements other tests for errors

    private static String doubleQuote(final String value) {
        return value.replaceAll("'", "\"");
    }

    private static abstract class ExchangeAnswer implements Answer<Void> {
        @Override
        public Void answer(final InvocationOnMock invocation) throws Throwable {
            Exchange exchange = (Exchange) invocation.getArguments()[0];
            answer(exchange);
            return null;
        }

        protected abstract void answer(final Exchange exchange);
    }
}
