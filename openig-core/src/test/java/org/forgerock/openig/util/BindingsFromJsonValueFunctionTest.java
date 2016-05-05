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

package org.forgerock.openig.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BindingsFromJsonValueFunctionTest {

    @Test
    public void shouldBuildBindings() throws Exception {
        JsonValue value = json(object(field("foo", "bar")));

        Bindings bindings = value.as(new BindingsFromJsonValueFunction());

        Expression<String> expression = Expression.valueOf("Hello ${foo}", String.class);
        assertThat(expression.eval(bindings)).isEqualTo("Hello bar");
    }

    @Test
    public void shouldBuildComplexBindings() throws Exception {
        JsonValue value = json(object(field("foo", object(field("bar", 42)))));

        Bindings bindings = value.as(new BindingsFromJsonValueFunction());

        Expression<String> expression = Expression.valueOf("The ultimate answer is ${foo.bar}", String.class);
        assertThat(expression.eval(bindings)).isEqualTo("The ultimate answer is 42");
    }
}
