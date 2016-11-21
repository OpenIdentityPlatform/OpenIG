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

import static org.assertj.core.data.MapEntry.entry;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.test.assertj.AssertJJsonValueAssert.assertThat;
import static org.forgerock.openig.Files.temporaryJsonFile;
import static org.forgerock.openig.el.Bindings.bindings;

import java.io.File;
import java.io.IOException;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Bindings;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ResolveLocationJsonValueFunctionTest {

    private ResolveLocationJsonValueFunction resolveLocation;

    @BeforeMethod
    public void setUp() throws Exception {
        resolveLocation = new ResolveLocationJsonValueFunction();
    }

    /**
     * $location is a value : its content replaces the value
     */
    @Test
    public void shouldReplaceMapValue() {
        JsonValue value = json(object(field("quix", object(field("$location",
                                                                 urlForFile("bar.json"))))));

        JsonValue resolvedJsonValue = value.as(resolveLocation);

        assertThat(resolvedJsonValue.get("quix")).isObject().contains(entry("foo", "bar"));
    }

    /**
     * $location is an element of the array : its content is inserted into the array
     */
    @Test
    public void shouldReplaceArrayValue() {
        JsonValue value = json(array("pim", object(field("$location", urlForFile("pam.json"))), "poum"));

        JsonValue resolvedJsonValue = value.as(resolveLocation);

        assertThat(resolvedJsonValue).isArray().contains("pim", array("toto", "titi"), "poum");
    }

    @DataProvider
    public static Object[][] invalidLocations() {
        //@Checkstyle:off
        return new Object[][]{
                { json(object(field("$location", null))) },
                { json(object(field("$location", "blabla"))) }
        };
        //@Checkstyle:on
    }

    @Test(dataProvider = "invalidLocations", expectedExceptions = JsonValueException.class)
    public void shouldThrowExceptionIfNotValidURL(JsonValue value) throws Exception {
        value.as(resolveLocation);
    }

    @Test
    public void shouldEvaluateExpressions() throws IOException {
        // Create the test file
        File targetFile = temporaryJsonFile("openig-test-properties",
                                            json(object(field("testProperty", "testValue"))));
        JsonValue config = json(object(field("$location",
                                             "${pathToUrl(system['java.io.tmpdir'])}/${tempFilename}")));
        // Parsing $location should correctly derive the path from the system variable java.io.tmpdir and use the
        // property tempFilename
        Bindings bindings = bindings().bind("tempFilename", targetFile.getName());
        ResolveLocationJsonValueFunction resolveLocation = new ResolveLocationJsonValueFunction(bindings);
        JsonValue result = resolveLocation.apply(config);
        assertThat(result).isObject().containsExactly(entry("testProperty", "testValue"));
    }

    private String urlForFile(String file) {
        return getClass().getClassLoader().getResource("test-bindings/" + file).toString();
    }
}
