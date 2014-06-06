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

package org.forgerock.openig.regex;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.MapEntry.entry;
import static org.forgerock.openig.regex.Readers.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.testng.annotations.Test;

public class StreamPatternExtractorTest {
    @Test
    public void testSimple() throws Exception {
        StreamPatternExtractor extractor = new StreamPatternExtractor();
        extractor.getPatterns().put("extra-header", Pattern.compile("^X-(.*): "));
        extractor.getTemplates().put("extra-header", new PatternTemplate("Found header '$1'"));

        Map<String, String> actual = asMap(extractor.extract(reader("X-Hello: \"World\"", "Not-Extra: Hi")));
        assertThat(actual).containsOnly(entry("extra-header", "Found header 'Hello'"));
    }

    @Test
    public void testMultiPatternsMatching() throws Exception {
        StreamPatternExtractor extractor = new StreamPatternExtractor();
        extractor.getPatterns().put("header", Pattern.compile("(.*): \\\"(.*)\\\""));
        extractor.getTemplates().put("header", new PatternTemplate("$2"));
        extractor.getPatterns().put("name", Pattern.compile("(.*): "));
        extractor.getTemplates().put("name", new PatternTemplate("$1"));

        Map<String, String> actual = asMap(extractor.extract(reader("X-Hello: \"World\"")));
        assertThat(actual).containsOnly(entry("header", "World"),
                                        entry("name", "X-Hello"));
    }

    public static <K, V> Map<K, V> asMap(Iterable<Map.Entry<K, V>> iterable) {
        Map<K, V> map = new HashMap<K, V>();
        for (Map.Entry<K, V> item : iterable) {
            map.put(item.getKey(), item.getValue());
        }
        return map;
    }
}
