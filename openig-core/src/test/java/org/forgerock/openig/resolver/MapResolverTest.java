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

package org.forgerock.openig.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MapResolverTest {

    private MapResolver resolver;
    private Map<String, Integer> map;

    @BeforeMethod
    public void setUp() throws Exception {
        resolver = new MapResolver();
        map = new HashMap<>();
    }

    @Test
    public void shouldReturnNullWhenKeyIsNotFound() throws Exception {
        assertThat(resolver.get(map, "foo")).isNull();
    }

    @Test
    public void shouldReturnTheValueWhenKeyIsFound() throws Exception {
        map.put("foo", 42);
        assertThat(resolver.get(map, "foo")).isEqualTo(42);
    }
}
