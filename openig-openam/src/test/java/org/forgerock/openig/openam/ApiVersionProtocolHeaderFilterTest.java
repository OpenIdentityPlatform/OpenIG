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

package org.forgerock.openig.openam;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.http.routing.Version.version;
import static org.forgerock.json.resource.http.HttpUtils.PROTOCOL_VERSION_1;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;

import org.forgerock.http.Handler;
import org.forgerock.http.header.AcceptApiVersionHeader;
import org.forgerock.http.header.MalformedHeaderException;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.routing.Version;
import org.forgerock.services.context.Context;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ApiVersionProtocolHeaderFilterTest {

    private static final URI RESOURCE_URI = URI.create("http://example.com/resource.jpg");
    private static final Version RESOURCE_VERSION = version(2);
    private Request request;

    @Mock
    private Handler next;

    @BeforeMethod
    public void setUp() {
        initMocks(this);
        request = new Request();
        request.setUri(RESOURCE_URI);
        request.getHeaders().put(new AcceptApiVersionHeader(PROTOCOL_VERSION_1, RESOURCE_VERSION));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailWithNullProtocolVersion() throws Exception {
        new ApiVersionProtocolHeaderFilter(null);
    }

    @Test
    public void shouldFailWithMalformedHeader() throws Exception {
        // given
        request.getHeaders().put("Accept-API-Version", "malformed=1.0");

        // when
        final Response response = new ApiVersionProtocolHeaderFilter(version(1))
                .filter(null, request, next)
                .get();
        // then
        verifyZeroInteractions(next);
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getCause()).isInstanceOf(MalformedHeaderException.class);
    }

    @DataProvider
    private static Object[][] versions() {
        return new Object[][] {
            { version(1) },
            { version(2) } };
    }

    @Test(dataProvider = "versions")
    public void shouldAddHeader(final Version protocolVersion) throws Exception {
        // when
        new ApiVersionProtocolHeaderFilter(protocolVersion).filter(null, request, next);

        // then
        verify(next).handle(any(Context.class), eq(request));
        assertThat(request.getUri().asURI()).isEqualTo(RESOURCE_URI);
        final AcceptApiVersionHeader header = request.getHeaders().get(AcceptApiVersionHeader.class);
        assertThat(header.getProtocolVersion()).isEqualTo(protocolVersion);
        assertThat(header.getResourceVersion()).isEqualTo(RESOURCE_VERSION);
    }
}
