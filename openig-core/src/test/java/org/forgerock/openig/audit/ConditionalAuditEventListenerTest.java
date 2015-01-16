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

package org.forgerock.openig.audit;

import static java.util.Collections.*;
import static org.mockito.Mockito.*;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConditionalAuditEventListenerTest {

    @Mock
    private AuditEventListener delegate;
    private AuditEvent source;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        source = new AuditEvent(new AuditSource(Name.of("source")),
                                System.currentTimeMillis(),
                                new Exchange(),
                                singleton("tag#1"));
    }

    @DataProvider
    public static Object[][] delegatingConditions() throws ExpressionException {
        // @Checkstyle:off
        return new Object[][] {
                {Expression.valueOf("${true}")},
                {Expression.valueOf("${contains(tags, 'tag#1')}")},
                {Expression.valueOf("${source.name.leaf == 'source'}")}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "delegatingConditions")
    public void shouldDelegate(Expression condition) throws Exception {
        ConditionalAuditEventListener agent = new ConditionalAuditEventListener(delegate, condition);
        agent.onAuditEvent(source);
        verify(delegate).onAuditEvent(source);
    }

    @DataProvider
    public static Object[][] filteringConditions() throws ExpressionException {
        // @Checkstyle:off
        return new Object[][] {
                {Expression.valueOf("${false}")},
                {Expression.valueOf("a non boolean value")},
                {Expression.valueOf("${source.name.leaf == 'not the right name'}")},
                {Expression.valueOf("${source.wrongProperty == 'java.lang.Object'}")}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "filteringConditions")
    public void shouldNotDelegate(Expression condition) throws Exception {
        ConditionalAuditEventListener agent = new ConditionalAuditEventListener(delegate, condition);
        agent.onAuditEvent(source);
        verifyZeroInteractions(delegate);
    }
}
