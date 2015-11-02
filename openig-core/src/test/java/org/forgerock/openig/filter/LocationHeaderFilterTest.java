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
 * Copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;

import java.net.URI;
import java.util.Collections;

import org.forgerock.http.Handler;
import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test case for the LocationHeaderFilter
 */
@SuppressWarnings("javadoc")
public class LocationHeaderFilterTest {
    @DataProvider
    private Object[][] validConfigurations() {
        return new Object[][] {
            { json(object(
                    field("baseURI", "https://client.example.org/callback"))) },
            { json(object(
                    field("baseURI", "${exchange.uri}"))) } };
    }

    @DataProvider
    private Object[][] invalidConfigurations() {
        return new Object[][] {
            /* Not a String. */
            { json(object(
                    field("baseURI", 42))) } };
    }

    @Test(dataProvider = "invalidConfigurations", expectedExceptions = JsonValueException.class)
    public void shouldFailToCreateHeapletWhenRequiredAttributeIsMissing(final JsonValue config) throws Exception {
        final LocationHeaderFilter.Heaplet heaplet = new LocationHeaderFilter.Heaplet();
        heaplet.create(Name.of("LocationRewriter"), config, buildDefaultHeap());
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws Exception {
        final LocationHeaderFilter.Heaplet heaplet = new LocationHeaderFilter.Heaplet();
        final LocationHeaderFilter lhf = (LocationHeaderFilter) heaplet.create(Name.of("LocationRewriter"),
                                                                                       config,
                                                                                       buildDefaultHeap());
        assertThat(lhf).isNotNull();
    }

    @Test
    public void shouldRebaseToOriginalUriIfNotSet() throws Exception {
        final String expectedResult = "http://www.origin.com:443/path/to/redirected?a=1&b=2";

        final URI testRedirectionURI = new URI("http://app.example.com:8080/path/to/redirected?a=1&b=2");
        callFilter(new LocationHeaderFilter(), testRedirectionURI, expectedResult);
    }

    @Test
    public void caseChangeSchemeHostAndPort() throws Exception {


        String expectedResult = "https://proxy.example.com:443/path/to/redirected?a=1&b=2";

        URI testRedirectionURI = new URI("http://app.example.com:8080/path/to/redirected?a=1&b=2");

        LocationHeaderFilter filter = new LocationHeaderFilter();
        filter.setBaseURI(Expression.valueOf("https://proxy.example.com:443/", String.class));

        callFilter(filter, testRedirectionURI, expectedResult);
    }

    @Test
    public void caseChangeHost() throws Exception {


        String expectedResult = "http://proxy.example.com:8080/path/to/redirected?a=1&b=2";

        URI testRedirectionURI = new URI("http://app.example.com:8080/path/to/redirected?a=1&b=2");

        LocationHeaderFilter filter = new LocationHeaderFilter();
        filter.setBaseURI(Expression.valueOf("http://proxy.example.com:8080/", String.class));

        callFilter(filter, testRedirectionURI, expectedResult);
    }

    @Test
    public void caseChangePort() throws Exception {


        String expectedResult = "http://app.example.com:9090/path/to/redirected?a=1&b=2";

        URI testRedirectionURI = new URI("http://app.example.com:8080/path/to/redirected?a=1&b=2");

        LocationHeaderFilter filter = new LocationHeaderFilter();
        filter.setBaseURI(Expression.valueOf("http://app.example.com:9090/", String.class));

        callFilter(filter, testRedirectionURI, expectedResult);
    }

    @Test
    public void caseChangeScheme() throws Exception {


        String expectedResult = "https://app.example.com/path/to/redirected?a=1&b=2";

        URI testRedirectionURI = new URI("http://app.example.com/path/to/redirected?a=1&b=2");

        LocationHeaderFilter filter = new LocationHeaderFilter();
        filter.setBaseURI(Expression.valueOf("https://app.example.com/", String.class));

        callFilter(filter, testRedirectionURI, expectedResult);
    }

    @Test
    public void caseNoChange() throws Exception {

        String expectedResult = "http://app.example.com:8080/path/to/redirected?a=1&b=2";

        URI testRedirectionURI = new URI("http://app.example.com:8080/path/to/redirected?a=1&b=2");

        LocationHeaderFilter filter = new LocationHeaderFilter();
        filter.setBaseURI(Expression.valueOf("http://app.example.com:8080/", String.class));

        callFilter(filter, testRedirectionURI, expectedResult);
    }

    @Test
    public void caseBaseUriAsExpression() throws Exception {
        LocationHeaderFilter filter = new LocationHeaderFilter();
        filter.setBaseURI(Expression.valueOf("http://${contexts.attributes.attributes.host}:8080", String.class));

        AttributesContext attributesContext = new AttributesContext(new RootContext());
        attributesContext.getAttributes().put("host", "app.example.com");
        Context context = new UriRouterContext(attributesContext,
                                               null,
                                               null,
                                               Collections.<String, String>emptyMap(),
                                               new URI("http://www.origin.com:443"));


        // Prepare a response
        Response expectedResponse = new Response();
        expectedResponse.getHeaders().add(LocationHeader.NAME, "http://internal.example.com/redirected");
        expectedResponse.setStatus(Status.FOUND);

        ResponseHandler next = new ResponseHandler(expectedResponse);

        Response response = filter.filter(context, null, next).get();

        assertThat(response.getHeaders().getFirst(LocationHeader.NAME))
                .isEqualTo("http://app.example.com:8080/redirected");
    }

    @Test
    public void caseChangeHostWithEncodedLocation() throws Exception {
        String expectedResult = "http://proxy.example.com:8080/path/a%20b/redirected?a=1&b=%3D2";

        URI testRedirectionURI = new URI("http://app.example.com:8080/path/a%20b/redirected?a=1&b=%3D2");

        LocationHeaderFilter filter = new LocationHeaderFilter();
        filter.setBaseURI(Expression.valueOf("http://proxy.example.com:8080/", String.class));

        callFilter(filter, testRedirectionURI, expectedResult);
    }

    private void callFilter(LocationHeaderFilter filter, URI testRedirectionURI, String expectedResult)
            throws Exception {

        final Context context = new UriRouterContext(new RootContext(),
                                                     null,
                                                     null,
                                                     Collections.<String, String>emptyMap(),
                                                     new URI("http://www.origin.com:443"));

        Response expectedResponse = new Response();
        expectedResponse.getHeaders().put(LocationHeader.NAME, testRedirectionURI.toString());
        expectedResponse.setStatus(Status.FOUND);

        ResponseHandler next = new ResponseHandler(expectedResponse);

        Response response = filter.filter(context, null, next).get();

        LocationHeader header = LocationHeader.valueOf(response);
        assertThat(header.getLocationUri()).isEqualTo(expectedResult);
    }

    private static class ResponseHandler implements Handler {

        private final Response response;

        private ResponseHandler(final Response response) {
            this.response = response;
        }

        @Override
        public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
            return Promises.newResultPromise(response);
        }
    }
}
