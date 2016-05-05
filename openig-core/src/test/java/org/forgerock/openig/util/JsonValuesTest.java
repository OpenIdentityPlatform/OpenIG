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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.JsonValue.set;
import static org.forgerock.json.JsonValueFunctions.listOf;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.firstOf;
import static org.forgerock.openig.util.JsonValues.getWithDeprecation;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.forgerock.http.Handler;
import org.forgerock.json.JsonException;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.log.Logger;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class JsonValuesTest {

    @Mock
    private Heap heap;

    @Mock
    private Handler handler;

    @Mock
    private Logger logger;

    @Captor
    private ArgumentCaptor<JsonValue> captor;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldEvaluateObject() throws Exception {
        final JsonValue value = json(object(
                                        field("string", "${decodeBase64('VGhpcyBtZXRob2Q=')}"),
                                        field("array", array("${decodeBase64('ZXZhbHVhdGVz')}")),
                                        field("map", object(field("mapField",
                                                "${decodeBase64('YWxsIHN0cmluZyBleHByZXNzaW9ucw==')}"))),
                                        field("set", set("${decodeBase64('aW4gdGhlIGdpdmVuIG9iamVjdA==')}"))));
        final JsonValue transformed = value.as(evaluated());
        assertThat(transformed.get("string").asString()).isEqualTo("This method");
        assertThat(transformed.get("array").get(0).asString()).isEqualTo("evaluates");
        assertThat(transformed.get("map").get("mapField").asString()).isEqualTo("all string expressions");
        assertThat(transformed.get("set").iterator().next().asString()).isEqualTo("in the given object");
    }

    @Test
    public void shouldEvaluateObjectWithTypeTransformation() throws Exception {
        JsonValue value = json(object(field("boolean", "${true}"),
                                      field("array", "${array('one', 'two', 'three')}"),
                                      field("properties", "${system}")));

        JsonValue transformed = value.as(evaluated());

        assertThat(transformed.get("boolean").asBoolean()).isTrue();
        assertThat((String[]) transformed.get("array").getObject()).containsExactly("one", "two", "three");
        assertThat(transformed.get("properties").asMap(String.class)).containsKey("user.dir");
    }

    @Test
    public void shouldEvaluateObjectWhenAttributeEvaluationFails() throws Exception {
        final JsonValue value = json(object(field("string", "${decodeBase64('notBase64')}")));
        final JsonValue transformed = value.as(evaluated());
        assertThat(transformed.get("string").asString()).isNull();
    }

    @Test
    public void shouldEvaluateObjectWhenAttributeIsNotAnExpression() throws Exception {
        final JsonValue value = json(object(field("string", "$$$${{")));
        final JsonValue transformed = value.as(evaluated());
        assertThat(transformed.get("string").asString()).isEqualTo("$$$${{");
    }

    @Test
    public void shouldEvaluateLeafJsonValueWithExpression() throws Exception {
        JsonValue value = new JsonValue("${true} is good");
        JsonValue transformed = value.as(evaluated());
        assertThat(transformed.asString()).isEqualTo("true is good");
        assertThat(transformed).isNotSameAs(value);
    }

    @Test
    public void shouldLeaveUnchangedLeafJsonValueWithNoExpression() throws Exception {
        JsonValue value = new JsonValue("remains the same");
        JsonValue transformed = value.as(evaluated());
        assertThat(transformed.asString()).isEqualTo("remains the same");
        assertThat(transformed).isNotSameAs(value);
    }

    @Test
    public void shouldEvaluateExpressionToRealType() throws Exception {
        assertThat(json("${1 == 1}").as(evaluated()).isBoolean()).isTrue();
        assertThat(json("${8 * 5 + 2}").as(evaluated()).asInteger()).isEqualTo(42);
        assertThat(json("${join(array('foo', 'bar', 'quix'), '@')}").as(evaluated()).asString())
                .isEqualTo("foo@bar@quix");
    }

    @Test
    public void shouldUseBindingsWhenEvaluatingExpressionToRealType() throws Exception {
        Bindings bindings = Bindings.bindings().bind("i", 2);
        assertThat(json("${2 == i}").as(evaluated(bindings)).isBoolean()).isTrue();
        assertThat(json("${8 * 5 + i}").as(evaluated(bindings)).asInteger()).isEqualTo(42);
        assertThat(json("${join(array('foo', 'bar', 'quix'), i)}").as(evaluated(bindings)).asString())
                .isEqualTo("foo2bar2quix");
    }

    @Test(enabled = false,
          expectedExceptions = JsonException.class,
          expectedExceptionsMessageRegExp = "Expression 'incorrect \\$\\{expression' "
                                            + "\\(in /one\\) is not syntactically correct")
    public void shouldFailForIncorrectValueExpression() throws Exception {
        JsonValue mapValue = json(object(field("one", "incorrect ${expression")));
        mapValue.get("one").as(evaluated());
    }

    @Test
    public void shouldTransformListOfReferencesToListOfHeapObjectsWithSingleReference() throws Exception {
        when(heap.resolve(argThat(hasValue("RefOne")), eq(String.class))).thenReturn("Resolved object #1");
        JsonValue list = json(array("RefOne"));

        assertThat(list.as(listOf(requiredHeapObject(heap, String.class))))
                .containsExactly("Resolved object #1");
    }

    @Test
    public void shouldTransformListOfReferencesToListOfHeapObjectsWithMultipleReferences() throws Exception {
        when(heap.resolve(any(JsonValue.class), eq(String.class)))
                .thenReturn("Resolved object #1", "Resolved object #2", "Resolved object #3");
        JsonValue list = json(array("RefOne", "RefTwo", "RefThree"));

        assertThat(list.as(listOf(requiredHeapObject(heap, String.class))))
                .containsExactly("Resolved object #1", "Resolved object #2", "Resolved object #3");
    }

    @Test
    public void getWithDeprecationReturnsNonDeprecatedValue() {
        JsonValue config = json(object(field("new", "value")));
        assertThat(getWithDeprecation(config, logger, "new", "old").asString()).isEqualTo("value");
        verifyZeroInteractions(logger);
    }

    @Test
    public void getWithDeprecationReturnsNullValue() {
        JsonValue config = json(object(field("new", "value")));
        assertThat(getWithDeprecation(config, logger, "missing", "old").asString()).isNull();
        verifyZeroInteractions(logger);
    }

    @Test
    public void getWithDeprecationReturnsDeprecatedValue1() {
        JsonValue config = json(object(field("old", "value")));
        assertThat(getWithDeprecation(config, logger, "new", "old").asString()).isEqualTo("value");
        verify(logger).warning(anyString());
    }

    @Test
    public void getWithDeprecationReturnsDeprecatedValue2() {
        JsonValue config = json(object(field("older", "value")));
        assertThat(getWithDeprecation(config, logger, "new", "old", "older").asString()).isEqualTo(
                "value");
        verify(logger).warning(anyString());
    }

    @Test
    public void getWithDeprecationReturnsMostRecentValue1() {
        JsonValue config = json(object(field("new", "value1"), field("old", "value2")));
        assertThat(getWithDeprecation(config, logger, "new", "old", "older").asString()).isEqualTo(
                "value1");
        verify(logger).warning(anyString());
    }

    @Test
    public void shouldFirstOfReturnsNullWithNullNamesParameter() {
        JsonValue config = json(object(
                                    field("syn3", "plethora"),
                                    field("syn2", "overabundance"),
                                    field("syn1", "excess")));
        assertThat(firstOf(config, (String[]) null).isNull()).isTrue();
    }

    @Test
    public void shouldFirstOfSucceed() {
        JsonValue config = json(object(
                                    field("syn3", "plethora"),
                                    field("syn2", "overabundance"),
                                    field("syn1", "excess")));
        assertThat(firstOf(config, "syn1", "syn3").asString()).isEqualTo("excess");
        assertThat(firstOf(config, "syn3", "syn1").asString()).isEqualTo("plethora");
    }

    @Test
    public void shouldFirstOfSucceedWhenNullFieldInConfig() {
        JsonValue config = json(object(
                                    field("syn3", null),
                                    field("syn2", "overabundance"),
                                    field("syn1", "excess")));
        assertThat(firstOf(config, "syn3", "syn1").isNull()).isTrue();
    }

    @Test
    public void shouldFirstOfReturnsNullWhenNoMatch() {
        JsonValue config = json(object(
                                    field("syn3", "plethora"),
                                    field("syn2", "overabundance"),
                                    field("syn1", "excess")));
        assertThat(firstOf(config, "old", "ancient").isNull()).isTrue();
    }

    @Test
    public void getWithDeprecationReturnsMostRecentValue2() {
        JsonValue config = json(object(field("old", "value1"), field("older", "value2")));
        assertThat(getWithDeprecation(config, logger, "new", "old", "older").asString()).isEqualTo(
                "value1");
        verify(logger, times(2)).warning(anyString());
    }

    private static Matcher<JsonValue> hasValue(final Object value) {
        return new BaseMatcher<JsonValue>() {
            @Override
            public boolean matches(final Object item) {
                JsonValue json = (JsonValue) item;
                return value.equals(json.getObject());
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText(value.toString());
            }
        };
    }
}
