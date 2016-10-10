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

package org.forgerock.openig.heap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.Mockito.when;

import java.io.File;

import org.forgerock.openig.config.Environment;
import org.forgerock.openig.el.Expression;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EnvironmentHeapTest {

    private static final String PATH = "mock";

    @Mock
    private Environment environment;
    private EnvironmentHeap heap;
    private Expression<String> baseDirectory;
    private Expression<String> keyValue;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(environment.getBaseDirectory()).thenReturn(new File(PATH));
        heap = new EnvironmentHeap(Name.of("test"), environment);
        baseDirectory = Expression.valueOf("${openig.baseDirectory.path}", String.class);
        keyValue = Expression.valueOf("${key}", String.class);
    }

    @Test
    public void shouldGiveAccessToEnvironment() throws Exception {
        assertThat(baseDirectory.eval(heap.getProperties())).isEqualTo(PATH);
    }

    @Test
    public void shouldGiveAccessToEnvironmentWhenHeapAlsoContainsProperties() throws Exception {
        heap.init(json(object(field("properties", object(field("key", "value"))))));

        assertThat(baseDirectory.eval(heap.getProperties())).isEqualTo(PATH);
        assertThat(keyValue.eval(heap.getProperties())).isEqualTo("value");
    }

    @Test
    public void shouldGiveAccessToEnvironmentToChildHeaps() throws Exception {
        HeapImpl child = new HeapImpl(heap);
        child.init(json(object(field("properties", object(field("key", "value"))))));

        assertThat(baseDirectory.eval(child.getProperties())).isEqualTo(PATH);
        assertThat(keyValue.eval(child.getProperties())).isEqualTo("value");
    }
}
