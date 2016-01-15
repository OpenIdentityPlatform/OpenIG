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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EnforcerFilterTest {

    @Mock
    private Filter delegate;

    @Mock
    private Handler handler;

    @Mock
    private Logger logger;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldDelegateToTheRealFilter() throws Exception {
        EnforcerFilter enforcer = new EnforcerFilter(Expression.valueOf("${true}", Boolean.class), delegate, logger);
        Context context = new RootContext();
        enforcer.filter(context, null, handler);
        verify(delegate).filter(context, null, handler);
        verifyZeroInteractions(logger);
    }

    @DataProvider
    public static Object[][] conditionsEvaluatingToFalse() {
        // @Checkstyle:off
        return new Object[][] {
                { "${false}" },
                { "not a condition" },
                { "${attributes.missing}" }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "conditionsEvaluatingToFalse")
    public void shouldReturnAnInternalServerErrorForInvalidOrEvaluatedToFalseConditions(String condition)
            throws Exception {
        EnforcerFilter enforcer = new EnforcerFilter(Expression.valueOf(condition, Boolean.class), delegate, logger);
        Response response = enforcer.filter(new AttributesContext(new RootContext()), null, handler).get();
        assertThat(response.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
        assertThat(response.getCause()).isNull();
        verify(logger).error(anyString());
    }
}
