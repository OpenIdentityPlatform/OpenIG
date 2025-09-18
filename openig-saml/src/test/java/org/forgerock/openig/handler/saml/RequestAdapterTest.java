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
 * Portions copyright 2025 3A Systems LLC.
 */

package org.forgerock.openig.handler.saml;

import static java.util.Collections.list;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Arrays.array;

import java.net.URI;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.forgerock.http.protocol.Request;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RequestAdapterTest {

    public static final String BASE_URI = "http://www.example.org";
    public static final String HELLO_WORLD_PARAM = "hello=world";
    public static final String FIRST_MULTI_VALUED_PARAM = "multi_valued=one";
    public static final String SECOND_MULTI_VALUED_PARAM = "multi_valued=two%20three";

    @Mock
    private HttpServletRequest delegate;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @DataProvider
    public Object[][] requestsWithFormParameters() throws Exception {
        return new Object[][] {
            { buildRequestWithParametersInQuery() },
            { buildRequestWithParametersInPayload() },
            { buildRequestWithParametersInBothPayloadAndQuery() }
            // TODO add a case(s) where content type is missing ...
        };
    }

    private Request buildRequestWithParametersInQuery() throws Exception {
        Request request = new Request();
        request.setUri(new URI(BASE_URI.concat("?")
                                       .concat(HELLO_WORLD_PARAM)
                                       .concat("&")
                                       .concat(FIRST_MULTI_VALUED_PARAM)
                                       .concat("&")
                                       .concat(SECOND_MULTI_VALUED_PARAM)));
        return request;
    }

    private Request buildRequestWithParametersInPayload() throws Exception {
        Request request = new Request();
        request.getHeaders().add("Content-Type", "application/x-www-form-urlencoded");
        request.setUri(new URI(BASE_URI));
        request.setEntity(HELLO_WORLD_PARAM + "&" + FIRST_MULTI_VALUED_PARAM + "&"
                + SECOND_MULTI_VALUED_PARAM);
        return request;
    }

    private Request buildRequestWithParametersInBothPayloadAndQuery() throws Exception {
        Request request = new Request();
        request.getHeaders().add("Content-Type", "application/x-www-form-urlencoded");
        request.setUri(new URI(BASE_URI.concat("?").concat(HELLO_WORLD_PARAM)));
        request.setEntity(FIRST_MULTI_VALUED_PARAM + "&" + SECOND_MULTI_VALUED_PARAM);
        return request;
    }

    @Test(dataProvider = "requestsWithFormParameters")
    public void testGetParameter(final Request request) throws Exception {
        RequestAdapter adapter = new RequestAdapter(delegate, request);

        assertThat(adapter.getParameter("hello")).isEqualTo("world");
        assertThat(adapter.getParameter("multi_valued")).isEqualTo("one");
        assertThat(adapter.getParameter("unknown")).isNull();
    }

    @Test(dataProvider = "requestsWithFormParameters")
    public void testGetParameterMap(final Request request) throws Exception {
        RequestAdapter adapter = new RequestAdapter(delegate, request);

        final Map<String, String[]> params = adapter.getParameterMap();
        assertThat(params)
                .hasSize(2)
                .containsEntry("hello", array("world"))
                .containsEntry("multi_valued", array("one", "two three"));
    }

    @Test(dataProvider = "requestsWithFormParameters")
    public void testGetParameterNames(final Request request) throws Exception {
        RequestAdapter adapter = new RequestAdapter(delegate, request);

        assertThat(list(adapter.getParameterNames()))
                .contains("hello", "multi_valued");
    }

    @Test(dataProvider = "requestsWithFormParameters")
    public void testGetParameterValues(final Request request) throws Exception {
        RequestAdapter adapter = new RequestAdapter(delegate, request);

        assertThat(adapter.getParameterValues("hello")).contains("world");
        assertThat(adapter.getParameterValues("multi_valued")).contains("one", "two three");
        assertThat(adapter.getParameterValues("unknown")).isNull();
    }
}
