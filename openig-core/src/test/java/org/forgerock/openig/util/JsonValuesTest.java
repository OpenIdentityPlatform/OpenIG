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

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.JsonValue.*;
import static org.forgerock.openig.util.JsonValues.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.forgerock.http.Handler;
import org.forgerock.json.JsonException;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.log.Logger;
import org.forgerock.util.time.Duration;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
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
        final JsonValue transformed = evaluate(value, logger);
        assertThat(transformed.get("string").asString()).isEqualTo("This method");
        assertThat(transformed.get("array").get(0).asString()).isEqualTo("evaluates");
        assertThat(transformed.get("map").get("mapField").asString()).isEqualTo("all string expressions");
        assertThat(transformed.get("set").iterator().next().asString()).isEqualTo("in the given object");
        verifyZeroInteractions(logger);
    }

    @Test
    public void shouldEvaluateObjectWithTypeTransformation() throws Exception {
        JsonValue value = json(object(field("boolean", "${true}"),
                                      field("array", "${array('one', 'two', 'three')}"),
                                      field("properties", "${system}")));

        JsonValue transformed = evaluate(value, logger);

        assertThat(transformed.get("boolean").asBoolean()).isTrue();
        assertThat((String[]) transformed.get("array").getObject()).containsExactly("one", "two", "three");
        assertThat(transformed.get("properties").asMap(String.class)).containsKey("user.dir");
        verifyZeroInteractions(logger);
    }

    @Test
    public void shouldEvaluateObjectWhenAttributeEvaluationFails() throws Exception {
        final JsonValue value = json(object(field("string", "${decodeBase64('notBase64')}")));
        final JsonValue transformed = evaluate(value, logger);
        assertThat(transformed.get("string").asString()).isNull();
        verify(logger).warning(anyString());
    }

    @Test
    public void shouldEvaluateObjectWhenAttributeIsNotAnExpression() throws Exception {
        final JsonValue value = json(object(field("string", "$$$${{")));
        final JsonValue transformed = evaluate(value, logger);
        assertThat(transformed.get("string").asString()).isEqualTo("$$$${{");
        verify(logger).warning(anyString());
    }

    @Test
    public void shouldEvaluateLeafJsonValueWithExpression() throws Exception {
        JsonValue value = new JsonValue("${true} is good");
        JsonValue transformed = evaluateJsonStaticExpression(value);
        assertThat(transformed.asString()).isEqualTo("true is good");
        assertThat(transformed).isNotSameAs(value);
    }

    @Test
    public void shouldLeaveUnchangedLeafJsonValueWithNoExpression() throws Exception {
        JsonValue value = new JsonValue("remains the same");
        JsonValue transformed = evaluateJsonStaticExpression(value);
        assertThat(transformed.asString()).isEqualTo("remains the same");
        assertThat(transformed).isNotSameAs(value);
    }

    @Test
    public void shouldIgnoreNonStringValues() throws Exception {
        JsonValue intValue = new JsonValue(42);
        assertThat(evaluateJsonStaticExpression(intValue).asInteger()).isEqualTo(42);

        JsonValue nullValue = new JsonValue(null);
        assertThat(evaluateJsonStaticExpression(nullValue).getObject()).isNull();

        JsonValue booleanValue = new JsonValue(true);
        assertThat(evaluateJsonStaticExpression(booleanValue).asBoolean()).isTrue();

        JsonValue listValue = json(array("one", "two", "${three}"));
        assertThat(evaluateJsonStaticExpression(listValue).asList(String.class))
                .containsExactly("one", "two", "${three}");

        JsonValue mapValue = json(object(field("one", 1), field("two", 2L), field("three", "${three}")));
        assertThat(evaluateJsonStaticExpression(mapValue).asMap())
                .containsExactly(entry("one", 1),
                                 entry("two", 2L),
                                 entry("three", "${three}"));
    }

    @Test
    public void shouldEvaluateExpressionToRealType() throws Exception {
        assertThat(evaluateJsonStaticExpression(json("${1 == 1}")).isBoolean()).isTrue();
        assertThat(evaluateJsonStaticExpression(json("${8 * 5 + 2}")).asInteger()).isEqualTo(42);
        assertThat(evaluateJsonStaticExpression(json("${join(array('foo', 'bar', 'quix'), '@')}")).asString())
                .isEqualTo("foo@bar@quix");
    }

    @Test
    public void shouldEvaluateAsInteger() throws Exception {
        assertThat(asInteger(json("${1+1}"))).isEqualTo(2);
        assertThat(asInteger(json(null))).isNull();
    }

    @Test(expectedExceptions = JsonValueException.class, dataProvider = "notEvaluableAsInteger")
    public void shouldNotEvaluateAsInteger(JsonValue node) throws Exception {
        asInteger(node);
    }

    @DataProvider
    public Object[][] notEvaluableAsInteger() {
        return new Object[][] {
            { json(true) },
            { json("${1==1}") }
        };
    }

    @Test
    public void shouldEvaluateAsDuration() throws Exception {
        Duration duration = asDuration(json("3 seconds"));
        assertThat(duration.getValue()).isEqualTo(3);
        assertThat(duration.getUnit()).isEqualTo(TimeUnit.SECONDS);

        assertThat(asDuration(json(null))).isNull();
    }

    @Test(expectedExceptions = JsonValueException.class, dataProvider = "notEvaluableAsDuration")
    public void shouldNotEvaluateAsDuration(JsonValue node) throws Exception {
        asDuration(node);
    }

    @DataProvider
    public Object[][] notEvaluableAsDuration() {
        return new Object[][] {
            { json(true) },
            { json("${1==1}") },
            { json("blah blah") }
        };
    }

    @Test
    public void shouldEvaluateAsBoolean() throws Exception {
        assertThat(asBoolean(json("${1 == 1}"))).isTrue();
        assertThat(asBoolean(json(null))).isNull();
    }

    @Test(expectedExceptions = JsonValueException.class, dataProvider = "notEvaluableAsBoolean")
    public void shouldNotEvaluateAsBoolean(JsonValue node) throws Exception {
        asBoolean(node);
    }

    @DataProvider
    public Object[][] notEvaluableAsBoolean() {
        return new Object[][] {
            { json("foo") },
            { json(42) },
            { json(object(field("ultimateAnswer", 42))) }
        };
    }

    @Test(expectedExceptions = JsonException.class,
          expectedExceptionsMessageRegExp = "Expression 'incorrect \\$\\{expression' "
                                            + "\\(in /one\\) is not syntactically correct")
    public void shouldFailForIncorrectValueExpression() throws Exception {
        JsonValue mapValue = json(object(field("one", "incorrect ${expression")));
        evaluateJsonStaticExpression(mapValue.get("one"));
    }

    @Test
    public void shouldTransformListOfReferencesToListOfHeapObjectsWithSingleReference() throws Exception {
        when(heap.resolve(argThat(hasValue("RefOne")), eq(String.class))).thenReturn("Resolved object #1");
        JsonValue list = json(array("RefOne"));

        assertThat(list.asList(ofRequiredHeapObject(heap, String.class)))
                .containsExactly("Resolved object #1");
    }

    @Test
    public void shouldTransformListOfReferencesToListOfHeapObjectsWithMultipleReferences() throws Exception {
        when(heap.resolve(any(JsonValue.class), eq(String.class)))
                .thenReturn("Resolved object #1", "Resolved object #2", "Resolved object #3");
        JsonValue list = json(array("RefOne", "RefTwo", "RefThree"));

        assertThat(list.asList(ofRequiredHeapObject(heap, String.class)))
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
