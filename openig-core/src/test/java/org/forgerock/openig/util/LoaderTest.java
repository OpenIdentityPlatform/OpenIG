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

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class LoaderTest {

    private static int counter = 0;

    @BeforeMethod
    public void before() {
        counter = 0;
    }

    @Test
    public void newInstance() {
        final Object obj = Loader.newInstance("org.forgerock.openig.util.LoaderTest$MyColor");
        assertThat(obj).isNotNull();
        assertThat(obj).isInstanceOf(MyColor.class);
        assertThat(counter).isEqualTo(1);

        Loader.newInstance("org.forgerock.openig.util.LoaderTest$MyColor");
        assertThat(counter).isEqualTo(2);
    }

    @Test
    public void newInstanceReturnsNullWhenClassNotFound() {
        assertThat(Loader.newInstance("org.forgerock.openig.util.LoaderTest$BadInnerClass")).isNull();
    }

    @Test
    public void newInstanceReturnsNullWhenClassNotFound2() {
        assertThat(Loader.newInstance("org.forgerock.openig.util.LoaderTest$BadInnerClass", LoaderTest.class)).isNull();
    }

    @Test
    public void newInstanceReturnsNullWhenAWrongTypeIsSpecified() {
        assertThat(counter).isEqualTo(0);
        final Object obj = Loader.newInstance("org.forgerock.openig.util.LoaderTest$MyColor", LoaderTest.class);
        assertThat(obj).isNull();
        assertThat(counter).isEqualTo(1);
    }

    @Test
    public void getResource() {
        final URL data = getClass().getResource("resource.properties");
        assertThat(data.getFile()).isNotNull();
    }

    @Test
    public void getMissingResourceReturnsNull() {
        assertThat(Loader.getResource("resource_bad.properties")).isNull();
    }

    @Test(expectedExceptions = java.lang.NullPointerException.class)
    public void testLoadMapDoesNotAllowNullServiceType() {
        Loader.loadMap(String.class, null);
    }

    @Test
    public void loadMap() throws IOException {
        final Map<String, Color> map = Loader.loadMap(String.class, Color.class);
        assertThat(map).isNotNull();
        assertThat(map.size()).isGreaterThanOrEqualTo(2);
        assertThat(map).containsKey("#0080FF").containsKey("#A75CA6");
    }

    @Test
    public void loadMapExample2() {
        final Map<String, MyColor> map = Loader.loadMap(String.class, MyColor.class);
        assertThat(map).isNotNull();
        assertThat(map).isEmpty();
    }

    @Test(expectedExceptions = java.lang.NullPointerException.class)
    public void loadListDoesNotAllowNullServiceType() {
        Loader.loadList(null);
    }

    @Test()
    public void loadList() {
        final List<Color> list = Loader.loadList(Color.class);
        assertThat(list.size()).isGreaterThanOrEqualTo(2);
    }

    @Test()
    public void loadListExample2() {
        final List<MyColor> list = Loader.loadList(MyColor.class);
        assertThat(list).isNotNull();
        assertThat(list).isEmpty();
    }

    /** An inner class used in this test case only. */
    public static class MyColor implements Color {
        public MyColor() {
            counter += 1;
        }

        @Override
        public String getKey() {
            return "#A75CA6";
        }
    }

    /** An inner class used in this test case only. */
    public static class Blue implements Color {
        @Override
        public String getKey() {
            return "#0080FF";
        }
    }
}
