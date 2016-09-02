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

package org.forgerock.openig.handler.resources;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.header.HeaderUtil.formatDate;
import static org.forgerock.http.protocol.Status.FOUND;
import static org.forgerock.http.protocol.Status.METHOD_NOT_ALLOWED;
import static org.forgerock.http.protocol.Status.NOT_FOUND;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.openig.handler.resources.ResourceHandler.NOT_MODIFIED;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;

import org.forgerock.http.header.ContentTypeHeader;
import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.services.context.RootContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ResourceHandlerTest {

    @Mock
    private ResourceSet resourceSet;

    @Mock
    private Resource resource;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @DataProvider
    public static Object[][] deniedMethods() {
        // @Checkstyle:off
        return new Object[][] {
                { "POST" },
                { "HEAD" },
                { "PATCH" },
                { "DELETE" },
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "deniedMethods")
    public void shouldRejectNonGetMethod(String method) throws Exception {
        ResourceHandler handler = new ResourceHandler(Collections.<ResourceSet>emptyList());
        Response response = handler.handle(null, new Request().setMethod(method))
                                   .get();

        assertThat(response.getStatus()).isEqualTo(METHOD_NOT_ALLOWED);
    }

    @Test
    public void shouldAcceptGetMethod() throws Exception {
        when(resourceSet.find(anyString())).thenReturn(resource);
        when(resource.getType()).thenReturn("text/html");

        ResourceHandler handler = new ResourceHandler(singletonList(resourceSet));
        UriRouterContext context = new UriRouterContext(new RootContext(),
                                                        "",
                                                        "/index.html",
                                                        Collections.<String, String>emptyMap());
        Response response = handler.handle(context, new Request().setMethod("GET"))
                                   .get();

        assertThat(response.getStatus()).isEqualTo(OK);
        assertThat(response.getHeaders().get(ContentTypeHeader.class).getType())
                .isEqualTo("text/html");
        assertThat(response.getHeaders().getFirst("Last-Modified"))
                .isEqualTo("Thu, 01 Jan 1970 00:00:00 GMT");
    }

    @Test
    public void shouldReturn404ForUnknownResources() throws Exception {

        ResourceHandler handler = new ResourceHandler(singletonList(resourceSet));
        UriRouterContext context = new UriRouterContext(new RootContext(),
                                                        "",
                                                        "/index.html",
                                                        Collections.<String, String>emptyMap());
        Response response = handler.handle(context, new Request().setMethod("GET"))
                                   .get();

        assertThat(response.getStatus()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void shouldNotDefineContentTypeWhenResourceIsMissingExtension() throws Exception {
        when(resourceSet.find(anyString())).thenReturn(resource);

        ResourceHandler handler = new ResourceHandler(singletonList(resourceSet));
        UriRouterContext context = new UriRouterContext(new RootContext(),
                                                        "",
                                                        "/my-resource",
                                                        Collections.<String, String>emptyMap());
        Response response = handler.handle(context, new Request().setMethod("GET"))
                                   .get();

        assertThat(response.getStatus()).isEqualTo(OK);
        assertThat(response.getHeaders().getFirst(ContentTypeHeader.NAME)).isNull();
    }

    @Test
    public void shouldServeNotModifiedResources() throws Exception {
        when(resourceSet.find(anyString())).thenReturn(resource);
        when(resource.getLastModified()).thenReturn(0L);

        ResourceHandler handler = new ResourceHandler(singletonList(resourceSet));
        UriRouterContext context = new UriRouterContext(new RootContext(),
                                                        "",
                                                        "/index.html",
                                                        Collections.<String, String>emptyMap());
        Request request = new Request().setMethod("GET");
        request.getHeaders().put("If-Modified-Since", formatDate(new Date(System.currentTimeMillis())));
        Response response = handler.handle(context, request)
                                   .get();

        assertThat(response.getStatus()).isEqualTo(NOT_MODIFIED);
    }

    @Test
    public void shouldServeModifiedResources() throws Exception {
        when(resourceSet.find(anyString())).thenReturn(resource);
        when(resource.hasChangedSince(0L)).thenReturn(true);

        ResourceHandler handler = new ResourceHandler(singletonList(resourceSet));
        UriRouterContext context = new UriRouterContext(new RootContext(),
                                                        "",
                                                        "/index.html",
                                                        Collections.<String, String>emptyMap());
        Request request = new Request().setMethod("GET");
        request.getHeaders().put("If-Modified-Since", formatDate(new Date(0L)));
        Response response = handler.handle(context, request)
                                   .get();

        assertThat(response.getStatus()).isEqualTo(OK);
    }

    @Test
    public void shouldServeWelcomePage() throws Exception {
        when(resourceSet.find("index.html")).thenReturn(resource);

        ResourceHandler handler = new ResourceHandler(singletonList(resourceSet), singletonList("index.html"));
        UriRouterContext context = new UriRouterContext(new RootContext(),
                                                        "",
                                                        "",
                                                        Collections.<String, String>emptyMap());
        Request request = new Request().setUri("/").setMethod("GET");
        Response response = handler.handle(context, request)
                                   .get();

        assertThat(response.getStatus()).isEqualTo(OK);
    }

    @Test
    public void shouldReturnsNotFoundWhenNoWelcomePage() throws Exception {
        ResourceHandler handler = new ResourceHandler(singletonList(resourceSet), singletonList("index.html"));
        UriRouterContext context = new UriRouterContext(new RootContext(),
                                                        "",
                                                        "",
                                                        Collections.<String, String>emptyMap());
        Request request = new Request().setUri("/").setMethod("GET");
        Response response = handler.handle(context, request)
                                   .get();

        assertThat(response.getStatus()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void shouldRedirectWhenAccessingTheWelcomePageWithoutSlashEndedUri() throws Exception {
        ResourceHandler handler = new ResourceHandler(singletonList(resourceSet), singletonList("index.html"));
        UriRouterContext context = new UriRouterContext(new RootContext(),
                                                        "",
                                                        "",
                                                        Collections.<String, String>emptyMap());
        Request request = new Request().setUri("/openig/console").setMethod("GET");
        Response response = handler.handle(context, request)
                                   .get();

        assertThat(response.getStatus()).isEqualTo(FOUND);
        assertThat(response.getHeaders().get(LocationHeader.class).getLocationUri())
                .isEqualTo("/openig/console/");
    }
}
