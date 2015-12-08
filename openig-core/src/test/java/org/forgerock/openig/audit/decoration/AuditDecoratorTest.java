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

package org.forgerock.openig.audit.decoration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.json;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Deprecated
@SuppressWarnings("javadoc")
public class AuditDecoratorTest extends org.forgerock.openig.audit.decoration.AbstractAuditTest {

    @Mock
    private Filter filter;

    @Mock
    private Handler handler;

    @Mock
    private Context context;

    @BeforeMethod
    public void setUp() throws Exception {
        super.setUp();
        when(context.getName()).thenReturn(Name.of("config.json", "Router"));
        when(handler.handle(any(org.forgerock.services.context.Context.class), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));
    }

    @Test
    public void shouldDecorateFilter() throws Exception {
        AuditDecorator decorator = new AuditDecorator(auditSystem);
        assertThat(decorator.decorateFilter(filter, json(null), context)).isInstanceOf(AuditFilter.class);
    }

    @Test
    public void shouldDecorateHandler() throws Exception {
        AuditDecorator decorator = new AuditDecorator(auditSystem);
        assertThat(decorator.decorateHandler(handler, json(null), context)).isInstanceOf(AuditHandler.class);
    }

    @Test
    public void shouldContainsDefaultTagsOnly() throws Exception {
        final Handler decorated = buildDecoratedHandler(json(""));
        decorated.handle(new RootContext(), new Request());

        verify(auditSystem, atLeastOnce()).onAuditEvent(captor.capture());
        final org.forgerock.openig.audit.AuditEvent event = captor.getValue();
        assertThat(event.getTags()).containsExactly("response", "completed");
    }

    @Test
    public void shouldExtractAdditionalSingleTag() throws Exception {
        final Handler decorated = buildDecoratedHandler(json("tag-1"));
        decorated.handle(new RootContext(), new Request());

        verify(auditSystem, atLeastOnce()).onAuditEvent(captor.capture());
        final org.forgerock.openig.audit.AuditEvent event = captor.getValue();
        assertThat(event.getTags()).containsExactly("tag-1", "response", "completed");
    }

    @Test
    public void shouldExtractMultipleAdditionalTags() throws Exception {
        final Handler decorated = buildDecoratedHandler(json(array("tag-1", "tag-2")));
        decorated.handle(new RootContext(), new Request());

        verify(auditSystem, atLeastOnce()).onAuditEvent(captor.capture());
        final org.forgerock.openig.audit.AuditEvent event = captor.getValue();
        assertThat(event.getTags()).containsExactly("tag-1", "tag-2", "response", "completed");
    }

    @Test
    public void shouldExtractAdditionalTagsButNotduplicateIfTheyAreIdentical() throws Exception {
        final Handler decorated = buildDecoratedHandler(json(array("tag-1", "tag-1")));
        decorated.handle(new RootContext(), new Request());

        verify(auditSystem, atLeastOnce()).onAuditEvent(captor.capture());
        final org.forgerock.openig.audit.AuditEvent event = captor.getValue();
        assertThat(event.getTags()).containsExactly("tag-1", "response", "completed");
    }

    private Handler buildDecoratedHandler(final JsonValue decoratorConfig) throws HeapException {
        final AuditDecorator decorator = new AuditDecorator(auditSystem);
        return decorator.decorateHandler(handler, decoratorConfig, context);
    }
}
