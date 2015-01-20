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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.resolver.Resolver.*;

import java.net.URI;

import org.forgerock.http.Form;
import org.forgerock.http.Request;
import org.forgerock.openig.resolver.RequestResolver;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RequestResolverTest {

    private static final String DEFAULT_URI = "www.example.com";
    private static final String ENTITY = "<html><p>Welcome back</p></html>";
    private static final String VERSION = "HTTP/1.1";

    private Request request;
    private RequestResolver resolver;

    @BeforeMethod
    public void setUp() throws Exception {
        request = new Request();
        request.setUri(DEFAULT_URI);
        request.setEntity(ENTITY);
        request.setVersion(VERSION);
        request.setMethod("GET");

        resolver = new RequestResolver();
    }

    @Test
    public void shouldGetUnknownAttributeReturnsUnresolved() {
        assertThat(resolver.get(request, "unknown")).isEqualTo(UNRESOLVED);
    }

    @Test
    public void shouldGetMethodSucceed() {
        assertThat(resolver.get(request, "method")).isEqualTo(request.getMethod());
    }

    @Test
    public void shouldGetCookiesSucceed() {
        assertThat(resolver.get(request, "cookies")).isSameAs(request.getCookies());
    }

    @Test
    public void shouldGetUriSucceed() {
        assertThat(resolver.get(request, "uri")).isEqualTo(request.getUri());
    }

    /** See OPENIG-404 */
    @Test(enabled = false)
    public void shouldSetMethodSucceed() {
        resolver.put(request, "method", "POST");
        assertThat(request.getMethod()).isEqualTo("POST");
    }

    @Test
    public void shouldSetMethodFailsWithInvalidValue() {
        assertThat(resolver.put(request, "method", 123)).isEqualTo(UNRESOLVED);
        assertThat(request.getMethod()).isEqualTo("GET");
    }

    @Test
    public void shouldSetUriSucceedWithString() {
        resolver.put(request, "uri", "http://example.com");
        assertThat(request.getUri().toString()).isEqualTo("http://example.com");
    }

    @Test
    public void shouldSetUriSucceedWithUri() throws Exception {
        resolver.put(request, "uri", new URI("http://example.com"));
        assertThat(request.getUri().toString()).isEqualTo("http://example.com");
    }

    @Test
    public void shouldSetUriFailsWithInvalidUriPattern() {
        assertThat(resolver.put(request, "uri", "http://[fails")).isEqualTo(UNRESOLVED);
        assertThat(request.getUri().toString()).isEqualTo(DEFAULT_URI);
    }

    @Test
    public void shouldGetEntitySucceed() {
        assertThat(resolver.get(request, "entity")).isSameAs(request.getEntity());
    }

    @Test
    public void shouldGetVersionSucceed() {
        assertThat(resolver.get(request, "version")).isEqualTo(request.getVersion());
    }

    /** See OPENIG-404 */
    @Test(enabled = false)
    public void shouldSetVersionSucceed() {
        resolver.put(request, "version", "HTTP/1.0");
        assertThat(request.getVersion()).isEqualTo("HTTP/1.0");
    }

    @Test
    public void shouldGetFormSucceed() {
        final Form f = new Form();
        f.add("foo", "bar");
        f.toRequestQuery(request);

        assertThat(((Form) resolver.get(request, "form")))
            .containsKey("foo")
            .containsValue(asList("bar"));
    }
}
