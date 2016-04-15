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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.authz.modules.oauth2.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.forgerock.authz.modules.oauth2.AccessTokenException;
import org.forgerock.authz.modules.oauth2.AccessTokenInfo;
import org.forgerock.http.Handler;
import org.forgerock.http.filter.ResponseHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.time.TimeService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OpenAmAccessTokenResolverTest {

    public static final String TOKEN = "ACCESS_TOKEN";

    @Mock
    private TimeService time;

    @Captor
    private ArgumentCaptor<Context> contextCaptor;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    private Response response(final Status status, final String content) throws Exception {
        return new Response(status).setEntity(content);
    }

    @Test
    public void shouldProduceAnAccessToken() throws Exception {
        when(time.now()).thenReturn(0L);
        Handler client = spy(new ResponseHandler(response(Status.OK, doubleQuote("{'expires_in':10, "
                + "'access_token':'ACCESS_TOKEN', "
                + "'scope': [ 'email', 'name' ]}"))));
        OpenAmAccessTokenResolver resolver = new OpenAmAccessTokenResolver(client, time, "/oauth2/tokeninfo");

        final RootContext context = new RootContext();
        AccessTokenInfo token = resolver.resolve(context, TOKEN).get();
        assertThat(token.getToken()).isEqualTo(TOKEN);
        assertThat(token.getExpiresAt()).isEqualTo(10000L);

        verify(client).handle(contextCaptor.capture(), any(Request.class));
        assertThat(contextCaptor.getValue()).isSameAs(context);
    }

    @Test(expectedExceptions = AccessTokenException.class)
    public void shouldThrowAnOAuthTokenExceptionCausedByAnError() throws Exception {

        //Given
        Handler client = new ResponseHandler(response(Status.BAD_REQUEST, doubleQuote("{'error':'ERROR'}")));
        OpenAmAccessTokenResolver resolver = new OpenAmAccessTokenResolver(client, time, "/oauth2/tokeninfo");

        //When
        resolver.resolve(new RootContext(), TOKEN).getOrThrow();
    }

    private static String doubleQuote(final String value) {
        return value.replaceAll("'", "\"");
    }
}
