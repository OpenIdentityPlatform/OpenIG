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

package org.forgerock.openig.heap;

import static org.assertj.core.api.Assertions.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class NameTest {

    @Test
    public void shouldBuildNewName() throws Exception {
        Name name = Name.of("test");
        assertThat(name.getLeaf()).isEqualTo("test");
        assertThat(name.getParent()).isNull();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailForNullLocalName() throws Exception {
        Name.of((String) null);
    }

    @Test
    public void shouldBuildNameWithParent() throws Exception {
        Name name = Name.of("parent", "local");
        assertThat(name.getLeaf()).isEqualTo("local");
        assertThat(name.getParent().getLeaf()).isEqualTo("parent");
    }

    @Test
    public void shouldBuildNameWithParent2() throws Exception {
        Name name = Name.of("grand-parent", "parent", "local");
        assertThat(name.getLeaf()).isEqualTo("local");
        assertThat(name.getParent().getLeaf()).isEqualTo("parent");
        assertThat(name.getParent().getParent().getLeaf()).isEqualTo("grand-parent");
    }

    @Test
    public void shouldBuildNameFromType() throws Exception {
        Name name = Name.of(NameTest.class);
        assertThat(name.getLeaf()).isEqualTo("NameTest");
        assertThat(name.getParent()).isNull();
    }

    @DataProvider
    public static Object[][] fullNames() {
        // @Checkstyle:off
        return new Object[][] {
                {Name.of("Router"), "Router"},
                {Name.of("config.json", "Router"), "config.json+Router"},
                {Name.of("config.json", "route.json", "SomeFilter"), "config.json+route.json+SomeFilter"}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "fullNames")
    public void shouldPrintFullName(final Name name, final String expected) throws Exception {
        assertThat(name.getFullyQualifiedName()).isEqualTo(expected);
    }

    @DataProvider
    public static Object[][] scopedNames() {
        // @Checkstyle:off
        return new Object[][] {
                {Name.of("Router"), "Router"},
                {Name.of("config.json", "Router"), "config.json+Router"},
                {Name.of("config.json", "route.json", "SomeFilter"), "route.json+SomeFilter"}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "scopedNames")
    public void shouldPrintScopedName(final Name name, final String expected) throws Exception {
        assertThat(name.getScopedName()).isEqualTo(expected);
    }

    @Test
    public void shouldBeEquals() throws Exception {
        assertThat(Name.of("a")).isEqualTo(Name.of("a"));
        assertThat(Name.of("a", "b")).isEqualTo(Name.of("a", "b"));
    }
}
