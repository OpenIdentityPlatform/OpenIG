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

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.util.JsonValueUtil.*;
import static org.mockito.Mockito.when;

import org.forgerock.json.fluent.JsonException;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.heap.Heap;
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
}
