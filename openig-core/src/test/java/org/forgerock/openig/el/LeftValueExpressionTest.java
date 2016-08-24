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

package org.forgerock.openig.el;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.el.Bindings.bindings;

import java.util.Map;

import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class LeftValueExpressionTest {

    @Test
    public void setAttribute() throws ExpressionException {
        AttributesContext context = new AttributesContext(new RootContext());
        Bindings bindings = bindings(context, null);
        @SuppressWarnings("rawtypes")
        LeftValueExpression<Map> expr = LeftValueExpression.valueOf("${contexts.attributes.attributes.testmap}",
                                                                    Map.class);
        expr.set(bindings, singletonMap("foo", "bar"));
        Expression<String> foo = Expression.valueOf("${contexts.attributes.attributes.testmap.foo}", String.class);
        Expression<String> easyAccess = Expression.valueOf("${attributes.testmap.foo}", String.class);
        assertThat(foo.eval(bindings)).isEqualTo(easyAccess.eval(bindings)).isEqualTo("bar");
    }

    @Test
    public void testUsingIntermediateBean() throws Exception {
        ExpressionTest.ExternalBean bean = new ExpressionTest.ExternalBean(
                new ExpressionTest.InternalBean("Hello World"));

        Bindings bindings = bindings("bean", bean);
        assertThat(Expression.valueOf("${bean.internal.value}", String.class)
                             .eval(bindings))
                .isEqualTo("Hello World");
        LeftValueExpression.valueOf("${bean.internal.value}", String.class).set(bindings, "ForgeRock OpenIG");
        assertThat(bean.getInternal().getValue()).isEqualTo("ForgeRock OpenIG");
    }

    @Test
    public void setRequestEntityAsJson() throws Exception {
        Request request = new Request();
        LeftValueExpression.valueOf("${request.entity.json}", Map.class)
                  .set(bindings("request", request), object(field("k1", "v1"), field("k2", 123)));
        assertThat(request.getEntity()).isNotNull();
        assertThat(request.getEntity().getString())
                .isEqualTo("{\"k1\":\"v1\",\"k2\":123}");
    }

    @Test
    public void setRequestEntityAsString() throws Exception {
        Request request = new Request();
        LeftValueExpression.valueOf("${request.entity.string}", String.class)
                  .set(bindings("request", request), "mary mary quite contrary");
        assertThat(request.getEntity()).isNotNull();
        assertThat(request.getEntity().getString()).isEqualTo("mary mary quite contrary");
    }
}
