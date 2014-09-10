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

package org.forgerock.openig.util;

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.util.JsonValueUtil.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.forgerock.json.fluent.JsonException;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.log.Logger;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class JsonValueUtilTest {

    @Mock
    private Heap heap;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
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

    @Test(expectedExceptions = JsonException.class,
          expectedExceptionsMessageRegExp = "Expression 'incorrect \\$\\{expression' "
                                            + "\\(in /one\\) is not syntactically correct")
    public void shouldFailForIncorrectValueExpression() throws Exception {
        JsonValue mapValue = json(object(field("one", "incorrect ${expression")));
        evaluateJsonStaticExpression(mapValue.get("one"));
    }

    @Test
    public void shouldTransformListOfReferencesToListOfHeapObjectsWithSingleReference() throws Exception {
        when(heap.get("RefOne")).thenReturn("Resolved object #1");
        JsonValue list = json(array("RefOne"));

        assertThat(list.asList(ofRequiredHeapObject(heap, String.class)))
                .containsExactly("Resolved object #1");
    }

    @Test
    public void shouldTransformListOfReferencesToListOfHeapObjectsWithMultipleReferences() throws Exception {
        when(heap.get("RefOne")).thenReturn("Resolved object #1");
        when(heap.get("RefTwo")).thenReturn("Resolved object #2");
        when(heap.get("RefThree")).thenReturn("Resolved object #3");
        JsonValue list = json(array("RefOne", "RefTwo", "RefThree"));

        assertThat(list.asList(ofRequiredHeapObject(heap, String.class)))
                .containsExactly("Resolved object #1", "Resolved object #2", "Resolved object #3");
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void shouldFailForUnresolvableReferences() throws Exception {
        JsonValue list = json(array("RefOne"));
        list.asList(ofRequiredHeapObject(heap, String.class));
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void shouldFailForIncorrectUseOfReferences() throws Exception {
        JsonValue list = json(array(42));
        list.asList(ofRequiredHeapObject(heap, String.class));
    }

    @Test
    public void testJsonCompatibilityBoxedPrimitiveType() throws Exception {
        JsonValueUtil.checkJsonCompatibility("boolean", true);
        JsonValueUtil.checkJsonCompatibility("integer", 1);
        JsonValueUtil.checkJsonCompatibility("short", (short) 12);
        JsonValueUtil.checkJsonCompatibility("long", -42L);
        JsonValueUtil.checkJsonCompatibility("float", 42.3F);
        JsonValueUtil.checkJsonCompatibility("double", 3.14159D);
        JsonValueUtil.checkJsonCompatibility("char", 'a');
        JsonValueUtil.checkJsonCompatibility("byte", (byte) 'c');
    }

    @Test
    public void testJsonCompatibilityWithCharSequences() throws Exception {
        JsonValueUtil.checkJsonCompatibility("string", "a string");
        JsonValueUtil.checkJsonCompatibility("string-buffer", new StringBuffer("a string buffer"));
        JsonValueUtil.checkJsonCompatibility("string-builder", new StringBuilder("a string builder"));
    }

    @Test
    public void testJsonCompatibilityWithArrayOfString() throws Exception {
        String[] strings = {"one", "two", "three"};
        JsonValueUtil.checkJsonCompatibility("array", strings);
    }

    @Test
    public void testJsonCompatibilityWithListOfString() throws Exception {
        String[] strings = {"one", "two", "three"};
        JsonValueUtil.checkJsonCompatibility("array", asList(strings));
    }

    @Test
    public void testJsonCompatibilityWithMapOfString() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("one", "one");
        map.put("two", "two");
        map.put("three", "three");
        JsonValueUtil.checkJsonCompatibility("map", map);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void shouldNotAcceptUnsupportedTypes() throws Exception {
        JsonValueUtil.checkJsonCompatibility("object", new Object());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*'list\\[1\\]'.*")
    public void shouldWriteErrorTrailForIncorrectList() throws Exception {
        JsonValueUtil.checkJsonCompatibility("list", asList("one", new Object(), "three"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*'map/object'.*")
    public void shouldWriteErrorTrailForIncorrectMap() throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("one", "one");
        map.put("object", new Object());
        JsonValueUtil.checkJsonCompatibility("map", map);
    }

    @Test
    public void getWithDeprecationReturnsNonDeprecatedValue() {
        JsonValue config = json(object(field("new", "value")));
        Logger logger = mock(Logger.class);
        assertThat(getWithDeprecation(config, logger, "new", "old").asString()).isEqualTo("value");
        verifyZeroInteractions(logger);
    }

    @Test
    public void getWithDeprecationReturnsNullValue() {
        JsonValue config = json(object(field("new", "value")));
        Logger logger = mock(Logger.class);
        assertThat(getWithDeprecation(config, logger, "missing", "old").asString()).isNull();
        verifyZeroInteractions(logger);
    }

    @Test
    public void getWithDeprecationReturnsDeprecatedValue1() {
        JsonValue config = json(object(field("old", "value")));
        Logger logger = mock(Logger.class);
        assertThat(getWithDeprecation(config, logger, "new", "old").asString()).isEqualTo("value");
        verify(logger).warning(anyString());
    }

    @Test
    public void getWithDeprecationReturnsDeprecatedValue2() {
        JsonValue config = json(object(field("older", "value")));
        Logger logger = mock(Logger.class);
        assertThat(getWithDeprecation(config, logger, "new", "old", "older").asString()).isEqualTo(
                "value");
        verify(logger).warning(anyString());
    }

    @Test
    public void getWithDeprecationReturnsMostRecentValue1() {
        JsonValue config = json(object(field("new", "value1"), field("old", "value2")));
        Logger logger = mock(Logger.class);
        assertThat(getWithDeprecation(config, logger, "new", "old", "older").asString()).isEqualTo(
                "value1");
        verify(logger).warning(anyString());
    }

    @Test
    public void getWithDeprecationReturnsMostRecentValue2() {
        JsonValue config = json(object(field("old", "value1"), field("older", "value2")));
        Logger logger = mock(Logger.class);
        assertThat(getWithDeprecation(config, logger, "new", "old", "older").asString()).isEqualTo(
                "value1");
        verify(logger, times(2)).warning(anyString());
    }
}
