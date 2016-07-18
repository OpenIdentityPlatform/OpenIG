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
import static org.forgerock.openig.util.JsonValues.heapObjectNameOrPointer;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class HeapObjectNameOrPointerJsonTransformFunctionTest {

    private HeapObjectNameOrPointerJsonTransformFunction heapObjectNameOrPointer;

    @BeforeMethod
    public void setUp() throws Exception {
        heapObjectNameOrPointer = new HeapObjectNameOrPointerJsonTransformFunction();
    }

    @DataProvider
    public static Object[][] refObjects() {
        //@Checkstyle:off
        return new Object[][]{
                // Refer to an already defined object
                { json("foo"),
                  "foo" },
                // Inline declaration with a provided name
                { json(object(field("type", "WelcomeHandler"),
                              field("name", "Inline"))),
                  "Inline" },
                // Anonymous inline declaration
                { json(object(field("type", "WelcomeHandler"))),
                  "{WelcomeHandler}/" }
        };
        //@Checkstyle:on
    }

    @Test(dataProvider = "refObjects")
    public void shouldReturnHeapObjectRef(JsonValue refObject, String expectedRef) throws Exception {
        assertThat(refObject.as(heapObjectNameOrPointer)).isEqualTo(expectedRef);
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void shouldThrowAnExceptionIfNotCorrectHeapObjectRef() throws Exception {
        json(true).as(heapObjectNameOrPointer);
    }

    @Test
    public void shouldResolveInlineObjectNamingWithNoNameProvidedInDeepHierarchy() throws Exception {
        JsonValue root = json(object(field("heap",
                                           object(field("objects",
                                                        array(object(field("type", "WelcomeHandler"))))))));
        assertThat(root.get("heap").get("objects").get(0).as(heapObjectNameOrPointer()))
                .isEqualTo("{WelcomeHandler}/heap/objects/0");
    }
}
