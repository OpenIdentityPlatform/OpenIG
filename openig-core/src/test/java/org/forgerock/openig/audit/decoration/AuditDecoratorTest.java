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

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.mockito.Mockito.*;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.audit.AuditEvent;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AuditDecoratorTest extends AbstractAuditTest {

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
        when(handler.handle(any(org.forgerock.http.Context.class), any(Request.class)))
                .thenReturn(Promises.<Response, ResponseException>newResultPromise(new Response()));
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
    public void shouldExtractAdditionalTags() throws Exception {
        AuditDecorator decorator = new AuditDecorator(auditSystem);
        Handler decorated = decorator.decorateHandler(handler, json(array("tag-1", "tag-2")), context);
        decorated.handle(new Exchange(), new Request());

        verify(auditSystem, atLeastOnce()).onAuditEvent(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getTags()).contains("tag-1", "tag-2");
    }
}
