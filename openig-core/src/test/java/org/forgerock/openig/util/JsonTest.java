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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.util;

import static java.util.Arrays.*;
import static org.forgerock.openig.util.Json.*;

import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class JsonTest {

    @Test
    public void testJsonCompatibilityBoxedPrimitiveType() throws Exception {
        checkJsonCompatibility("boolean", true);
        checkJsonCompatibility("integer", 1);
        checkJsonCompatibility("short", (short) 12);
        checkJsonCompatibility("long", -42L);
        checkJsonCompatibility("float", 42.3F);
        checkJsonCompatibility("double", 3.14159D);
        checkJsonCompatibility("char", 'a');
        checkJsonCompatibility("byte", (byte) 'c');
    }

    @Test
    public void testJsonCompatibilityWithCharSequences() throws Exception {
        checkJsonCompatibility("string", "a string");
        checkJsonCompatibility("string-buffer", new StringBuffer("a string buffer"));
        checkJsonCompatibility("string-builder", new StringBuilder("a string builder"));
    }

    @Test
    public void testJsonCompatibilityWithArrayOfString() throws Exception {
        String[] strings = {"one", "two", "three"};
        checkJsonCompatibility("array", strings);
    }

    @Test
    public void testJsonCompatibilityWithListOfString() throws Exception {
        String[] strings = {"one", "two", "three"};
        checkJsonCompatibility("array", asList(strings));
    }

    @Test
    public void testJsonCompatibilityWithMapOfString() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("one", "one");
        map.put("two", "two");
        map.put("three", "three");
        checkJsonCompatibility("map", map);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void shouldNotAcceptUnsupportedTypes() throws Exception {
        checkJsonCompatibility("object", new Object());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*'list\\[1\\]'.*")
    public void shouldWriteErrorTrailForIncorrectList() throws Exception {
        checkJsonCompatibility("list", asList("one", new Object(), "three"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*'map/object'.*")
    public void shouldWriteErrorTrailForIncorrectMap() throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("one", "one");
        map.put("object", new Object());
        checkJsonCompatibility("map", map);
    }
}
