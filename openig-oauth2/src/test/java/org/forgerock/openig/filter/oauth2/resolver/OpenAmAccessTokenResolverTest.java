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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.resolver;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.filter.oauth2.AccessToken;
import org.forgerock.openig.filter.oauth2.FailureHandler;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.openig.filter.oauth2.ResponseHandler;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OpenAmAccessTokenResolverTest {

    public static final String TOKEN = "ACCESS_TOKEN";

    @Mock
    private TimeService time;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    private Response response(final int status, final String content) throws Exception {
        return new Response().setStatus(status).setEntity(content);
    }

    @Test
    public void shouldProduceAnAccessToken() throws Exception {
        when(time.now()).thenReturn(0L);
        Handler client = new ResponseHandler(response(200, doubleQuote("{'expires_in':10, "
                                                                       + "'access_token':'ACCESS_TOKEN', "
                                                                       + "'scope': [ 'email', 'name' ]}")));
        OpenAmAccessTokenResolver resolver = new OpenAmAccessTokenResolver(client, time, "/oauth2/tokeninfo");

        AccessToken token = resolver.resolve(TOKEN);
        assertThat(token.getToken()).isEqualTo(TOKEN);
        assertThat(token.getExpiresAt()).isEqualTo(10000L);
    }

    @Test(expectedExceptions = OAuth2TokenException.class)
    public void shouldThrowAnOAuthTokenExceptionCausedByAnError() throws Exception {

        //Given
        Handler client = new ResponseHandler(response(400, doubleQuote("{'error':'ERROR'}")));
        OpenAmAccessTokenResolver resolver = new OpenAmAccessTokenResolver(client, time, "/oauth2/tokeninfo");

        //When
        resolver.resolve(TOKEN);
    }

    @Test(expectedExceptions = OAuth2TokenException.class)
    public void shouldThrowAnOAuthTokenExceptionCausedByAnIOException() throws Exception {

        //Given
        Handler client = new FailureHandler(new ResponseException(404));
        OpenAmAccessTokenResolver resolver = new OpenAmAccessTokenResolver(client, time, "/oauth2/tokeninfo");

        //When
        resolver.resolve(TOKEN);
    }

    // TODO Implements other tests for errors

    private static String doubleQuote(final String value) {
        return value.replaceAll("'", "\"");
    }
}
