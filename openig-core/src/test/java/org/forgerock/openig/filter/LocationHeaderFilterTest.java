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

import java.net.URI;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.testng.annotations.Test;

/**
 * Test case for the LocationHeaderFilter
 */
@SuppressWarnings("javadoc")
public class LocationHeaderFilterTest {

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
        filter.setBaseURI(Expression.valueOf("http://${exchange.host}:8080", String.class));

        Exchange exchange = new Exchange();
        exchange.put("host", "app.example.com");

        // Prepare a response
        exchange.response = new Response();
        exchange.response.getHeaders().add(LocationHeader.NAME, "http://internal.example.com/redirected");
        exchange.response.setStatus(Status.FOUND);

        ResponseHandler next = new ResponseHandler(exchange.response);

        Response response = filter.filter(exchange, null, next).get();

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

        Exchange exchange = new Exchange();
        exchange.response = new Response();
        exchange.response.getHeaders().add(LocationHeader.NAME, testRedirectionURI.toString());
        exchange.response.setStatus(Status.FOUND);

        ResponseHandler next = new ResponseHandler(exchange.response);

        Response response = filter.filter(exchange, null, next).get();

        LocationHeader header = LocationHeader.valueOf(response);
        assertThat(header.toString()).isNotNull();
        assertThat(expectedResult).isEqualTo(header.toString());
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
