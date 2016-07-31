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
package org.forgerock.openig.filter.oauth2.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.DiscoveryFilter.OPENID_SERVICE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;
import java.util.Collections;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.openig.filter.oauth2.client.DiscoveryFilter.AccountIdentifier;
import org.forgerock.openig.heap.Heap;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Unit tests for the discovery filter class. */
@SuppressWarnings("javadoc")
public class DiscoveryFilterTest {
    private static final String REL_OPENID_ISSUER = "&rel=http://openid.net/specs/connect/1.0/issuer";

    private Context context;

    @Captor
    private ArgumentCaptor<Request> captor;

    @Mock
    private Heap heap;

    @Mock
    private Handler handler;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        context = new UriRouterContext(new RootContext(),
                                       null,
                                       null,
                                       Collections.<String, String>emptyMap(),
                                       new URI("www.example.com"));
    }

    @DataProvider
    private Object[][] givenInputAndNormalizedIdentifierExtracted() {
        return new Object[][] {
            { "acct:alice@example.com", "acct:alice@example.com" },
            { "acct:juliet%40capulet.example@shopping.example.com",
              "acct:juliet%40capulet.example@shopping.example.com" },
            { "https://example.com/joe", "https://example.com/joe" },
            { "https://example.com:8080/joe", "https://example.com:8080/joe" },
            { "http://www.example.org/foo.html#bar", "http://www.example.org/foo.html" },
            { "http://www.example.org#bar", "http://www.example.org" },
            { "https://example.org:8080/joe#bar", "https://example.org:8080/joe" },
            { "alice@example.com", "acct:alice@example.com" },
            { "alice@example.com:8080", "acct:alice@example.com:8080" },
            { "joe@example.com@example.org", "acct:joe@example.com@example.org" },
            { "joe@example.org:8080/path", "acct:joe@example.org:8080/path" }};
    }

    @DataProvider
    private Object[][] userInputAndFinalWebfingerProducedUri() {
        // According to RFC 3986, the characters ":" / "/" / "@" are not percent encoded in query strings
        return new Object[][] {
            // @Checkstyle:off
            // "decoded user input" -> "produced webfinger URI"
            {
                "acct:alice@example.com",
                "https://example.com/.well-known/webfinger?resource=acct:alice@example.com" + REL_OPENID_ISSUER },
            {
                "acct:joe@example.com",
                "https://example.com/.well-known/webfinger?resource=acct:joe@example.com" + REL_OPENID_ISSUER },
            {
                "https://example.com/joe",
                "https://example.com/.well-known/webfinger?resource=https://example.com/joe" + REL_OPENID_ISSUER },
            {
                "https://example.com:8080/",
                "https://example.com:8080/.well-known/webfinger?resource=https://example.com:8080/"
                        + REL_OPENID_ISSUER },
            {
                "acct:juliet@capulet.example@shopping.example.com",
                "https://shopping.example.com/.well-known/webfinger?"
                        + "resource=acct:juliet@capulet.example@shopping.example.com" + REL_OPENID_ISSUER },
            {
                "alice@example.com:8080",
                "https://example.com:8080/.well-known/webfinger?resource=acct:alice@example.com:8080"
                + REL_OPENID_ISSUER },
            {
                "https://www.example.com",
                "https://www.example.com/.well-known/webfinger?resource=https://www.example.com" + REL_OPENID_ISSUER },
            {
                "http://www.example.com",
                "http://www.example.com/.well-known/webfinger?resource=http://www.example.com" + REL_OPENID_ISSUER },
            {
                "joe@example.com@example.org",
                "https://example.org/.well-known/webfinger?resource=acct:joe@example.com@example.org"
                + REL_OPENID_ISSUER } };
            // @Checkstyle:on
    }

    @Test(expectedExceptions = DiscoveryException.class)
    public void shouldFailWhenInputIsNull() throws Exception {
        DiscoveryFilter.extractFromInput(null);
    }

    @Test(expectedExceptions = DiscoveryException.class)
    public void shouldFailWhenInputIsEmpty() throws Exception {
        DiscoveryFilter.extractFromInput("");
    }

    @Test(expectedExceptions = DiscoveryException.class)
    public void shouldFailWhenInputIsInvalid() throws Exception {
        DiscoveryFilter.extractFromInput("+://zorg");
    }

    @Test(dataProvider = "givenInputAndNormalizedIdentifierExtracted")
    public void shouldExtractParameters(final String input, final String expected) throws Exception {
        final AccountIdentifier account = DiscoveryFilter.extractFromInput(input);
        assertThat(account.getNormalizedIdentifier().toString()).isEqualTo(expected);
    }

    @Test(dataProvider = "userInputAndFinalWebfingerProducedUri")
    public void shouldReturnWebfingerUri(final String input, final String expected) throws Exception {
        final AccountIdentifier account = DiscoveryFilter.extractFromInput(input);
        final DiscoveryFilter df = new DiscoveryFilter(handler, heap);
        assertThat(df.buildWebFingerRequest(account).getUri().toString()).isEqualTo(expected);
    }

    @Test
    public void shouldPerformOpenIdIssuerDiscovery() throws Exception {
        // given
        final String givenWebFingerUri = "http://openam.example.com/.well-known/webfinger"
                                         + "?resource=http://openam.example.com/jackson"
                                         + "&rel=http://openid.net/specs/connect/1.0/issuer";

        final AccountIdentifier account = DiscoveryFilter.extractFromInput("http://openam.example.com/jackson");

        final Response response = new Response();
        response.setStatus(Status.OK);
        response.setEntity(json(object(field("links", array(
                                                        object(
                                                            field("rel" , "copyright"),
                                                            field("href", "http://www.example.com/copyright")),
                                                        object(
                                                           field("rel" , OPENID_SERVICE),
                                                           field("href", "http://localhost:8090/openam/oauth2")))))));
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        final DiscoveryFilter df = new DiscoveryFilter(handler, heap);
        final URI openIdWellKnownUri = df.performOpenIdIssuerDiscovery(context, account).getOrThrow();

        // then
        verify(handler).handle(eq(context), captor.capture());
        final Request request = captor.getValue();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getUri().toString()).isEqualTo(givenWebFingerUri);
        assertThat(openIdWellKnownUri.toString()).endsWith("/.well-known/openid-configuration");
    }

    @Test(expectedExceptions = DiscoveryException.class)
    public void shouldFailPerformOpenIdIssuerDiscoveryWhenServerResponseDoNotContainOpenIdLink() throws Exception {
        // given
        final AccountIdentifier account = DiscoveryFilter.extractFromInput("http://openam.example.com/jackson");

        final Response response = new Response();
        response.setStatus(Status.TEAPOT);
        response.setEntity(json(object(field("links", array(
                                                        object(
                                                            field("rel" , "copyright"),
                                                            field("href", "http://www.example.com/copyright")))))));

        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        final DiscoveryFilter df = new DiscoveryFilter(handler, heap);
        df.performOpenIdIssuerDiscovery(context, account).getOrThrow();
    }

    @Test(expectedExceptions = DiscoveryException.class)
    public void shouldFailPerformOpenIdIssuerDiscoveryWhenServerResponseContainInvalidJson() throws Exception {
        // given
        final AccountIdentifier account = DiscoveryFilter.extractFromInput("http://openam.example.com/jackson");

        final Response response = new Response();
        response.setStatus(Status.TEAPOT);
        response.setEntity(json(object(field("links", "not an array. Should fail"))));
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        final DiscoveryFilter df = new DiscoveryFilter(handler, heap);
        df.performOpenIdIssuerDiscovery(context, account).getOrThrow();
    }

    @Test(expectedExceptions = DiscoveryException.class)
    public void shouldFailWhenTheIssuerHrefIsNull() throws Exception {
        // given
        final AccountIdentifier account = DiscoveryFilter.extractFromInput("http://openam.example.com/jackson");

        final Response response = new Response();
        response.setStatus(Status.OK);
        response.setEntity(null);
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        final DiscoveryFilter df = new DiscoveryFilter(handler, heap);
        df.performOpenIdIssuerDiscovery(context, account).getOrThrow();
    }
}
