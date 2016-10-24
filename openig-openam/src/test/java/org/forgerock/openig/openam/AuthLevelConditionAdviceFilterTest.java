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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.openam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.routing.UriRouterContext.uriRouterContext;
import static org.forgerock.http.util.Uris.urlEncodeQueryParameterNameOrValue;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.net.URI;

import org.forgerock.http.Handler;
import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.json.JsonValue;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AuthLevelConditionAdviceFilterTest {

    private static final URI RESOURCE_URI = URI.create("http://example.com/index.html?foo=ultimate%20answer");
    private static final URI OPENAM_URI = URI.create("https://openam.example.com:8088/openam");
    private static final String MY_REALM = "/my realm";

    @Mock
    private Handler next;

    private AuthLevelConditionAdviceFilter filter;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        filter = new AuthLevelConditionAdviceFilter(OPENAM_URI, MY_REALM);
    }

    @Test
    public void shouldExecuteNextHandlerIfNoAuthLevelCondAdvice() throws Exception {
        filter.filter(contextWithPolicyDecisionAdvices(false), new Request(), next);

        verify(next).handle(any(Context.class), any(Request.class));
    }

    @Test
    public void shouldReturnRedirectResponseIfAuthLevelCondAdvice() throws Exception {
        Response response = filter.filter(contextWithPolicyDecisionAdvices(true), new Request(), next).get();

        assertThat(response.getStatus()).isEqualTo(Status.TEMPORARY_REDIRECT);
        String expectedQueryParameterAdvice = "<Advices>"
                + "<AttributeValuePair>"
                + "<Attribute name=\"AuthLevelConditionAdvice\"/>"
                + "<Value>42</Value>"
                + "</AttributeValuePair>"
                + "</Advices>";
        assertThat(response.getHeaders().getFirst(LocationHeader.NAME))
                .contains(OPENAM_URI.toASCIIString(),
                          "goto=" + urlEncodeQueryParameterNameOrValue(RESOURCE_URI.toASCIIString()),
                          "realm=" + urlEncodeQueryParameterNameOrValue(MY_REALM),
                          "authIndexType=composite_advice",
                          "authIndexValue=" + urlEncodeQueryParameterNameOrValue(expectedQueryParameterAdvice));
        verifyZeroInteractions(next);
    }

    private static Context contextWithPolicyDecisionAdvices(boolean withAdvice) {
        JsonValue advices = json(object());
        if (withAdvice) {
            advices.put("AuthLevelConditionAdvice", array("42"));
        }
        JsonValue attributes = json(object());
        return new PolicyDecisionContext(parentContext(), attributes, advices);
    }

    private static UriRouterContext parentContext() {
        return uriRouterContext(new RootContext()).originalUri(RESOURCE_URI).build();
    }
}
