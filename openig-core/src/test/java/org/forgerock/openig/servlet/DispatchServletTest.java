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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.servlet;

import static org.hamcrest.CoreMatchers.allOf;
import static org.mockito.Mockito.*;

import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.openig.log.ConsoleLogSink;
import org.forgerock.openig.log.Logger;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DispatchServletTest {

    @Mock
    private HttpServlet servlet;

    @Mock
    private Filter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private Logger logger;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        logger = new Logger(new ConsoleLogSink(), null);
    }

    /**
     * Filters are simply ignored.
     */
    //@Test
    public void testServletFilterAreInvoked() throws Exception {

        DispatchServlet dispatcher = new DispatchServlet();
        dispatcher.getBindings().add(new DispatchServlet.Binding(Pattern.compile(".*"), filter));
        dispatcher.getBindings().add(new DispatchServlet.Binding(Pattern.compile("/app/"), servlet));
        dispatcher.setLogger(logger);

        when(request.getPathInfo()).thenReturn("/app/endpoint");

        dispatcher.service(request, response);

        verify(servlet).service(any(HttpServletRequest.class), eq(response));
        verify(filter).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));

    }

    @Test
    public void testChainedDispatchServlet() throws Exception {

        DispatchServlet nested = new DispatchServlet();
        nested.getBindings().add(new DispatchServlet.Binding(Pattern.compile(".*"), servlet));
        nested.setLogger(logger);

        DispatchServlet main = new DispatchServlet();
        main.getBindings().add(new DispatchServlet.Binding(Pattern.compile("/app/"), nested));
        main.setLogger(logger);

        when(request.getPathInfo()).thenReturn("/app/endpoint");

        main.service(request, response);

        verify(servlet).service(argThat(allOf(new RequestPathInfoMatcher(null),
                                              new RequestServletPathMatcher("/endpoint"))),
                                        eq(response));

    }

    private static abstract class RequestMatcher extends BaseMatcher<HttpServletRequest> {

        @Override
        public boolean matches(final Object o) {
            return o instanceof HttpServletRequest && matchesRequest((HttpServletRequest) o);
        }

        protected abstract boolean matchesRequest(final HttpServletRequest request);

        @Override
        public void describeTo(final Description description) {

        }
    }

    public class RequestPathInfoMatcher extends RequestMatcher {

        private final String expected;

        public RequestPathInfoMatcher(final String expected) {
            this.expected = expected;
        }

        @Override
        protected boolean matchesRequest(final HttpServletRequest request) {
            final String pathInfo = request.getPathInfo();
            if (expected == null) {
                return (null == pathInfo);
            }
            return expected.equals(pathInfo);
        }
    }

    public class RequestServletPathMatcher extends RequestMatcher {

        private final String expected;

        public RequestServletPathMatcher(final String expected) {
            this.expected = expected;
        }

        @Override
        protected boolean matchesRequest(final HttpServletRequest request) {
            return expected.equals(request.getServletPath());
        }
    }
}
