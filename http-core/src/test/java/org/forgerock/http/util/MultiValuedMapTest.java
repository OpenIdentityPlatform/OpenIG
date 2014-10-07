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
package org.forgerock.http.util;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MultiValuedMapTest {

    @Test
    public void addAllowsNull() {
        final Coffee mine = new Coffee(getStandard());
        assertThat(mine).hasSize(2);
        mine.add(null, null);
        assertThat(mine).hasSize(3);
    }

    @Test
    public void add() {
        final Coffee mine = new Coffee(getStandard());
        assertThat(mine.get("milk-type")).contains("Half-and-half", "Plus");
        assertThat(mine.get("coffee")).contains("Arabica");
        assertThat(mine).hasSize(2);
        mine.add("syrup-type", "Vanilla");
        assertThat(mine).hasSize(3);
        assertThat(mine.get("syrup-type")).containsOnly("Vanilla");
    }

    @Test()
    public void addToExistingValues() {
        final Coffee mine = new Coffee(getStandard());
        assertThat(mine).hasSize(2);
        assertThat(mine.get("milk-type")).hasSize(2);

        mine.add("milk-type", "Steamed");
        assertThat(mine.get("milk-type")).hasSize(3);
        assertThat(mine.get("milk-type")).containsExactly("Half-and-half", "Plus", "Steamed");
    }

    @Test
    public void addAll() {
        final Coffee mine = new Coffee(getStandard());
        assertThat(mine).hasSize(2);
        mine.addAll("syrup-type", Arrays.asList("Vanilla", "Raspberry"));
        assertThat(mine).hasSize(3);
        assertThat(mine.get("syrup-type")).hasSize(2);
    }

    @Test()
    public void addAllMultivaluedMap() {
        final Coffee mine = new Coffee(getIcedCreamCoffee());
        assertThat(mine).hasSize(3);
        mine.addAll(new Coffee(getStandard()));
        assertThat(mine).hasSize(4);
        assertThat(mine.get("milk-type")).containsOnly("Half-and-half", "Plus", "Cream");
        assertThat(mine.get("coffee")).containsOnly("Arabica");
        assertThat(mine.get("brew")).containsOnly("Iced");
        assertThat(mine.get("sugar")).containsOnly("None");
    }

    @Test()
    public void addAllKeyCollection() {
        final Coffee mine = new Coffee(getIcedCreamCoffee());
        mine.addAll("milk-type", Arrays.asList("Steamed"));
        assertThat(mine).hasSize(3);
        assertThat(mine.get("milk-type")).containsOnly("Cream", "Steamed");
        assertThat(mine.get("brew")).containsOnly("Iced");
        assertThat(mine.get("sugar")).containsOnly("None");
    }

    @Test
    public void getFirst() {
        final Coffee mine = new Coffee(getStandard());
        assertThat(mine.getFirst("milk-type")).isEqualTo("Half-and-half");
    }

    @Test
    public void putSingleAllowsNull() {
        final Coffee mine = new Coffee(getStandard());
        assertThat(mine).hasSize(2);
        mine.putSingle(null, null);
        assertThat(mine).hasSize(3);
    }

    @Test
    public void putSingle() {
        final Coffee mine = new Coffee(getStandard());
        assertThat(mine).hasSize(2);
        mine.putSingle("syrup-type", "Caramel");
        assertThat(mine).hasSize(3);
        assertThat(mine.getFirst("syrup-type")).isEqualTo("Caramel");
    }

    @Test
    public void putSingleOverridesExistingValues() {
        final Coffee mine = new Coffee(getStandard());
        assertThat(mine).hasSize(2);
        assertThat(mine.get("milk-type")).containsOnly("Half-and-half", "Plus");
        mine.putSingle("milk-type", "Steamed");
        assertThat(mine.get("milk-type")).containsOnly("Steamed");
    }

    private static Map<String, List<String>> getStandard() {
        final Map<String, List<String>> custom = new LinkedHashMap<String, List<String>>();
        custom.put("milk-type", new LinkedList<String>(Arrays.asList("Half-and-half", "Plus")));
        custom.put("coffee", new LinkedList<String>(Arrays.asList("Arabica")));
        return custom;
    }

    private static Map<String, List<String>> getIcedCreamCoffee() {
        final Map<String, List<String>> custom = new LinkedHashMap<String, List<String>>();
        custom.put("milk-type", new LinkedList<String>(Arrays.asList("Cream")));
        custom.put("brew", new LinkedList<String>(Arrays.asList("Iced")));
        custom.put("sugar", new LinkedList<String>(Arrays.asList("None")));
        return custom;
    }

    private class Coffee extends MultiValueMap<String, String> {
        Coffee(Map<String, List<String>> map) {
            super(new LinkedHashMap<String, List<String>>(map));
        }
    }
}
