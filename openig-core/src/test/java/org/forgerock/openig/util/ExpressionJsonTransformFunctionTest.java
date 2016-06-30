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
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.JsonValue.set;
import static org.forgerock.json.test.assertj.AssertJJsonValueAssert.assertThat;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.util.test.assertj.Conditions.equalTo;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Bindings;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ExpressionJsonTransformFunctionTest {

    @Test
    public void shouldEvaluateStringNode() throws Exception {
        assertThat(evaluate(json("${true}"))).isBoolean().isTrue();
    }

    @Test
    public void shouldRetainJsonPointerValue() throws Exception {
        JsonPointer pointer = new JsonPointer("/object/2/attribute");
        JsonValue value = evaluate(new JsonValue(null, pointer));
        assertThat(value.getPointer()).isEqualTo(pointer);
    }

    @Test
    public void shouldEvaluateStringNodeWithNoExpression() throws Exception {
        JsonValue node;

        node = evaluate(json(true));
        assertThat(node).isBoolean().isTrue();

        node = evaluate(json(1));
        assertThat(node).isNumber().isLong().isEqualTo(1L);

        node = evaluate(json("foo"));
        assertThat(node).isString().isEqualTo("foo");

        node = evaluate(json(array("foo", "bar", "quix")));
        assertThat(node).isArray().containsExactly("foo", "bar", "quix");
    }

    @Test
    public void shouldUseBindingsWhenEvaluating() throws Exception {
        JsonValue node = evaluate(json("${foo + 2}"), bindings().bind("foo", 40));

        assertThat(node).isNumber().isLong().isEqualTo(42L);
    }

    @Test
    public void shouldSetNullJsonValueIfExpressionEvaluatesNull() throws Exception {
        JsonValue node = evaluate(json("${null}"));

        assertThat(node).isNull();
    }

    @Test
    public void shouldSetNullJsonValueIfExpressionEvaluatesIncorrectly() throws Exception {
        JsonValue node = evaluate(json("${foo + 2}"), bindings().bind("foo", true));

        assertThat(node).isNull();
    }

    @Test
    public void shouldIgnoreMalformedExpression() throws Exception {
        JsonValue node = json("${");
        assertThat(node).isString().isEqualTo("${");
    }

    @Test
    public void shouldEvaluateArithmeticOperationAsLong() throws Exception {
        JsonValue node = evaluate(json("${1+1}"));

        assertThat(node).isNumber().isLong().isEqualTo(2L);
    }

    @Test
    public void shouldPromoteExistingIntegerToLong() throws Exception {
        assertThat(evaluate(json(1))).isNumber().isLong().isEqualTo(1L);
        // Only the integer nodes are transformed
        assertThat(evaluate(json(3.5))).isNumber().isDouble().isEqualTo(3.5);
    }

    @Test
    public void shouldEvaluateArray() throws Exception {
        JsonValue node = evaluate(json(array("foo", "${'bar'}", "${null}")));

        assertThat(node).isArray()
                        .containsExactly("foo", "bar", null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldEvaluateSet() throws Exception {
        JsonValue node = evaluate(json(set(1, "${1}", "${3.5}")));

        // Even if the original Set is composed of 3 elements, then the transformed one has only 2 elements to preserve
        // the uniqueness of each element
        assertThat(node).isSet().containsExactly(1L, 3.5);
    }

    @Test
    public void shouldEvaluateMap() throws Exception {
        JsonValue node = evaluate(json(object(field("foo", "${1==1}"),
                                              field("bar", null))));

        assertThat(node).isObject()
                        .booleanIs("/foo", equalTo(true))
                        .hasNull("/bar");
    }

    @Test
    public void shouldEvaluateNestedJsonValues() throws Exception {
        JsonValue node = evaluate(json(object(field("foo", "${1==1}"),
                                              field("bar", array("foo", "${'bar'}", "${null}")))));

        assertThat(node).isObject()
                        .booleanIs("/foo", equalTo(true))
                        .hasArray("/bar").containsExactly("foo", "bar", null);
    }

    private JsonValue evaluate(JsonValue node) {
        return evaluate(node, bindings());
    }

    private JsonValue evaluate(JsonValue node, Bindings bindings) {
        return node.as(new ExpressionJsonTransformFunction(bindings));
    }

}
