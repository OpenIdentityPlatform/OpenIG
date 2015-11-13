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

package org.forgerock.openig.uma;

import static java.lang.String.format;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.hamcrest.CoreMatchers.allOf;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.forgerock.services.context.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class UmaResourceServerFilterTest {

    private static final String PAT = "1/fFAGRNJru1FTz70BzhT3Zg";
    private static final String RPT = "MzJmNDc3M2VjMmQzN";
    private static final String TICKET = "304f79ea-3e88-48ce-809b-ae57e3eb2f581";
    private static final String RS_ID = "dd645214-a720-4639-a644-ebf3161b69f91";

    private static final Expression<Boolean> TRUE;

    static {
        try {
            TRUE = Expression.valueOf("${true}", Boolean.class);
        } catch (ExpressionException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final ShareTemplate SHARE_TEMPLATE = new ShareTemplate(Pattern.compile(""),
                                                                          singletonList(new ShareTemplate
                                                                                  .Action(TRUE,
                                                                                          singleton("required"))));
    private static final Share SHARE = new Share(SHARE_TEMPLATE,
                                                 resourceSet(),
                                                 Pattern.compile(""),
                                                 PAT);
    @Mock
    private UmaSharingService service;
    @Mock
    private Handler handler;
    @Mock
    private Handler terminal;

    private Request request;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        request = new Request();
        when(service.findShare(request)).thenReturn(SHARE);
    }

    @Test
    public void shouldReturnTicketBecauseNoRptProvided() throws Exception {
        mockTicketCreation();
        UmaResourceServerFilter filter = new UmaResourceServerFilter(service, handler, null);

        Response response = filter.filter(null, request, terminal).get();

        assertThatTicketIsReturnedWithStatusForbidden(response);
        verifyZeroInteractions(terminal);
    }

    @Test
    public void shouldAuthorizeIncomingRequestWithValidRpt() throws Exception {
        request.getHeaders().put("Authorization", format("Bearer %s", RPT));
        mockTokenIntrospection(new Response(Status.OK).setEntity(validToken()));
        UmaResourceServerFilter filter = new UmaResourceServerFilter(service, handler, null);

        filter.filter(null, request, terminal);

        verify(terminal).handle(null, request);
    }

    private static Object validToken() {
        return object(field("active", true),
                      field("permissions", array(object(field("resource_set_id", RS_ID),
                                                        field("scopes", array("required"))))));
    }

    @Test
    public void shouldReturnTicketWhenInactiveRpt() throws Exception {
        request.getHeaders().put("Authorization", format("Bearer %s", RPT));
        mockTokenIntrospection(new Response(Status.OK).setEntity(inactiveToken()));
        mockTicketCreation();
        UmaResourceServerFilter filter = new UmaResourceServerFilter(service, handler, null);

        Response response = filter.filter(null, request, terminal).get();

        assertThatTicketIsReturnedWithStatusForbidden(response);
        verifyZeroInteractions(terminal);
    }

    private static Object inactiveToken() {
        return object(field("active", false));
    }

    @Test
    public void shouldReturnTicketWhenRptDoesNotContainsEnoughScopes() throws Exception {
        request.getHeaders().put("Authorization", format("Bearer %s", RPT));
        mockTokenIntrospection(new Response(Status.OK).setEntity(insufficientScopesToken()));
        mockTicketCreation();
        UmaResourceServerFilter filter = new UmaResourceServerFilter(service, handler, null);

        Response response = filter.filter(null, request, terminal).get();

        assertThatTicketIsReturnedWithStatusForbidden(response);
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).contains("insufficient_scope");
        verifyZeroInteractions(terminal);
    }

    private void assertThatTicketIsReturnedWithStatusForbidden(final Response response) throws IOException {
        assertThat(response.getStatus()).isEqualTo(Status.UNAUTHORIZED);
        assertThat(response.getHeaders()).containsKey("WWW-Authenticate");
        assertThat(response.getHeaders().getFirst("WWW-Authenticate"))
                .contains(format("ticket=\"%s\"", TICKET));
    }

    private Object insufficientScopesToken() {
        return object(field("active", true),
                      field("permissions", array(object(field("resource_set_id", RS_ID),
                                                        field("scopes", array("another-scope"))))));
    }

    private void mockTokenIntrospection(final Response response) throws URISyntaxException {
        URI introspectionUri = new URI("http://as.example.com/oauth2/introspect");
        when(service.getIntrospectionEndpoint()).thenReturn(introspectionUri);
        when(handler.handle(any(Context.class), argThat(hasUri(introspectionUri))))
                .thenReturn(Response.newResponsePromise(response));
    }

    private void mockTicketCreation() throws URISyntaxException {
        URI ticketUri = new URI("http://as.example.com/uma/permission_request");
        Response response = new Response(Status.CREATED);
        response.setEntity(object(field("ticket", TICKET)));
        when(service.getTicketEndpoint()).thenReturn(ticketUri);
        when(handler.handle(any(Context.class), argThat(allOf(hasUri(ticketUri), hasToken(PAT)))))
                .thenReturn(Response.newResponsePromise(response));
    }

    private static Matcher<Request> hasToken(final String token) {
        return new BaseMatcher<Request>() {
            @Override
            public boolean matches(final Object o) {
                if (o instanceof Request) {
                    Request request = (Request) o;
                    return format("Bearer %s", token).equals(request.getHeaders().getFirst("Authorization"));
                }
                return false;
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText(token);
            }
        };
    }

    private static Matcher<Request> hasUri(final URI uri) {
        return new BaseMatcher<Request>() {
            @Override
            public boolean matches(final Object o) {
                if (o instanceof Request) {
                    Request request = (Request) o;
                    return uri.equals(request.getUri().asURI());
                }
                return false;
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText(uri.toString());
            }
        };
    }

    private static JsonValue resourceSet() {
        return json(object(field("_id", RS_ID),
                           field("user_access_policy_uri",
                                 "https://openam.example.com:8443/openam/XUI/?realm=/#uma/share/" + RS_ID)));
    }

}
