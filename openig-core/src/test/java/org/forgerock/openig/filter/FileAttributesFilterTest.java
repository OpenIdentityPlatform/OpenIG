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

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;

import org.forgerock.http.filter.ResponseHandler;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.LeftValueExpression;
import org.forgerock.openig.text.SeparatedValuesFile;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.RootContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class FileAttributesFilterTest {

    @Mock
    private SeparatedValuesFile file;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void shouldProduceEmptyMapInCaseOfFailure() throws Exception {
        when(file.getRecord("username", "joe")).thenThrow(new IOException());

        Expression<String> value = Expression.valueOf("joe", String.class);
        LeftValueExpression<Map> target = LeftValueExpression.valueOf("${attributes.result}", Map.class);
        FileAttributesFilter filter = new FileAttributesFilter(file, "username", value, target);

        AttributesContext context = new AttributesContext(new RootContext());
        filter.filter(context, null, new ResponseHandler(Status.OK)).get();

        assertThat((Map) context.getAttributes().get("result")).isEmpty();
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void shouldProduceEmptyMapWhenSearchedEntryDoesNotExist() throws Exception {
        when(file.getRecord("username", "joe")).thenReturn(null);

        Expression<String> value = Expression.valueOf("joe", String.class);
        LeftValueExpression<Map> target = LeftValueExpression.valueOf("${attributes.result}", Map.class);
        FileAttributesFilter filter = new FileAttributesFilter(file, "username", value, target);

        AttributesContext context = new AttributesContext(new RootContext());
        filter.filter(context, null, new ResponseHandler(Status.OK)).get();

        assertThat((Map) context.getAttributes().get("result")).isEmpty();
    }
}
