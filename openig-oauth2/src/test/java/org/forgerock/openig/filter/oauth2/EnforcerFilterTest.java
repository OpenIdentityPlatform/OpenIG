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

package org.forgerock.openig.filter.oauth2;

import static org.mockito.Mockito.*;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EnforcerFilterTest {

    @Mock
    private Filter delegate;

    @Mock
    private Handler handler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldDelegateToTheRealFilter() throws Exception {
        EnforcerFilter enforcer = new EnforcerFilter(new Expression("${true}"), delegate);
        Exchange exchange = new Exchange();
        enforcer.filter(exchange, handler);
        verify(delegate).filter(exchange, handler);
    }

    @Test(expectedExceptions = HandlerException.class)
    public void shouldThrowAHandlerExceptionBecauseConditionIsNotVerified() throws Exception {
        EnforcerFilter enforcer = new EnforcerFilter(new Expression("${false}"), delegate);
        enforcer.filter(new Exchange(), handler);
    }

    @Test(expectedExceptions = HandlerException.class)
    public void shouldThrowAHandlerExceptionBecauseConditionIsInvalid() throws Exception {
        EnforcerFilter enforcer = new EnforcerFilter(new Expression("not a condition"), delegate);
        enforcer.filter(new Exchange(), handler);
    }
}
