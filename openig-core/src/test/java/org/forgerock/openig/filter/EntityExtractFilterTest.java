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
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.regex.Pattern;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.regex.PatternTemplate;
import org.forgerock.openig.util.MessageType;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EntityExtractFilterTest {

    @Mock
    private Handler terminalHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEntityExtractionFromRequestWithTemplates() throws Exception {
        EntityExtractFilter filter =
                new EntityExtractFilter(MessageType.REQUEST,
                                        Expression.valueOf("${attributes.result}", Map.class));
        filter.getExtractor().getPatterns().put("hello", Pattern.compile("Hello(.*)"));
        filter.getExtractor().getPatterns().put("none", Pattern.compile("Cannot match"));
        filter.getExtractor().getTemplates().put("hello", new PatternTemplate("$1"));

        AttributesContext context = new AttributesContext(new RootContext());
        Request request = new Request();
        request.setEntity("Hello OpenIG");

        filter.filter(context, request, terminalHandler);

        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) context.getAttributes().get("result");
        assertThat(results).containsOnly(
                entry("hello", " OpenIG"),
                entry("none", (String) null));
        verify(terminalHandler).handle(context, request);
    }

    @Test
    public void testEntityExtractionFromRequestWithNoTemplates() throws Exception {
        EntityExtractFilter filter =
                new EntityExtractFilter(MessageType.REQUEST,
                                        Expression.valueOf("${attributes.result}", Map.class));
        filter.getExtractor().getPatterns().put("hello", Pattern.compile("Hello(.*)"));
        filter.getExtractor().getPatterns().put("none", Pattern.compile("Cannot match"));

        AttributesContext context = new AttributesContext(new RootContext());
        Request request = new Request();
        request.setEntity("Hello OpenIG");

        filter.filter(context, request, terminalHandler);

        // The entry has a non-null value if it matches or a null value if it does not match
        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) context.getAttributes().get("result");
        assertThat(results).containsOnly(
                entry("hello", "Hello OpenIG"),
                entry("none", (String) null));
        verify(terminalHandler).handle(context, request);
    }

    @Test
    public void testResultMapIsEmptyWhenThereIsNoEntity() throws Exception {
        EntityExtractFilter filter =
                new EntityExtractFilter(MessageType.RESPONSE,
                                        Expression.valueOf("${attributes.result}", Map.class));
        filter.getExtractor().getPatterns().put("hello", Pattern.compile("Hello(.*)"));

        AttributesContext context = new AttributesContext(new RootContext());
        Response response = new Response();
        response.setEntity((String) null);

        when(terminalHandler.handle(context, null))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(response));

        filter.filter(context, null, terminalHandler);

        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) context.getAttributes().get("result");
        assertThat(results).containsOnly(entry("hello", (String) null));
    }
}
