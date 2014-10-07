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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.http.util;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CaseInsensitiveSetTest {

    private CaseInsensitiveSet set;
    private static String upper;
    private static String lower;

    @BeforeMethod
    public void before() {
        set = new CaseInsensitiveSet();
        upper = "AAA-" + UUID.randomUUID().toString().toUpperCase() + "-BBB";
        lower = upper.toLowerCase();
    }

    @Test
    public void testAddUpperKey() {
        set.add(upper);
        assertThat(set.iterator().next()).isEqualTo(upper);
    }

    @Test
    public void testAddUpperContains() {
        set.add(upper);
        assertThat(set).contains(upper);
    }

    @Test
    public void testAddUpperTwice() {
        boolean isAdded = set.add(upper);
        assertThat(isAdded).isTrue();
        assertThat(set).hasSize(1);
        isAdded = set.add(upper);
        assertThat(isAdded).isFalse();
        assertThat(set).hasSize(1);
        assertThat(set).containsOnly(upper);
    }

    @Test
    public void testAddUpperRemove() {
        set.add(upper);
        set.remove(upper);
        assertThat(set).isEmpty();
    }

    @Test
    public void testAddLowerKey() {
        set.add(lower);
        assertThat(set).hasSize(1);
        assertThat(set.iterator().next()).isEqualTo(lower);
        assertThat(set).hasSize(1).containsOnly(lower);
    }

    @Test
    public void testAddLowerRemove() {
        set.add(lower);
        set.remove(lower);
        assertThat(set).isEmpty();
    }

    @Test
    public void testAddUpperLowerRemove() {
        boolean isAdded = set.add(upper);
        assertThat(isAdded).isTrue();
        isAdded = set.add(upper);
        assertThat(isAdded).isFalse();
        set.remove(lower);
        assertThat(set).isEmpty();
    }

    @Test
    public void testAddUpperLowerClear() {
        set.add(lower);
        set.add(upper);
        assertThat(set).hasSize(1);
        set.clear();
        assertThat(set).isEmpty();
    }

    // TODO Stg wrong here : the set should contain only one entry as the addAll function did.
    @Test
    public void testAddFromListCollection() {
        final Collection<String> listOfValues = createRegularListWithUpperAndLowerCasedKeys();
        set = new CaseInsensitiveSet(createRegularListWithUpperAndLowerCasedKeys());
        assertThat(set).hasSize(listOfValues.size()); // 2?
        assertThat(set).contains(upper, lower);
    }

    @Test
    public void testAddFromListCollection2() {
        set = new CaseInsensitiveSet();
        final boolean isChanged = set.addAll(createRegularListWithUpperAndLowerCasedKeys());
        assertThat(isChanged).isTrue();
        assertThat(set).hasSize(1);
        // FIXME Looks horrible but it seems assertj doesn't use the right contains method.
        // assertThat(set).contains(upper).contains(lower);
        assertThat(set.contains(lower)).isTrue();
        assertThat(set.contains(upper)).isTrue();
        set.remove(lower);
        assertThat(set).isEmpty();
    }

    // TODO Stg wrong here : the set should contain only one entry as the addAll function did.
    @Test
    public void testAddFromSetCollection() {
        final Collection<String> listOfValues = createRegularSetWithUpperAndLowerCasedKeys();
        set = new CaseInsensitiveSet(listOfValues);
        assertThat(set).hasSize(listOfValues.size()); // 2?
        assertThat(set.contains(lower)).isTrue();
        assertThat(set.contains(upper)).isTrue();
        set.remove(lower);
        assertThat(set).hasSize(1);
    }

    @Test
    public void testAddFromSetCollection2() {
        set = new CaseInsensitiveSet();
        final boolean isChanged = set.addAll(createRegularSetWithUpperAndLowerCasedKeys());
        assertThat(isChanged).isTrue();
        assertThat(set).hasSize(1);
        assertThat(set.contains(lower)).isTrue();
        assertThat(set.contains(upper)).isTrue();
        set.remove(lower);
        assertThat(set).isEmpty();
    }

    @Test
    public void testContainsAllFromSetCollection() {
        final Collection<String> mySet = createRegularSetWithUpperAndLowerCasedKeys();
        set = new CaseInsensitiveSet();
        final boolean isChanged = set.addAll(mySet);
        assertThat(isChanged).isTrue();
        assertThat(set).hasSize(1);
        assertThat(set.contains(lower)).isTrue();
        assertThat(set.contains(upper)).isTrue();
        assertThat(set.containsAll(mySet)).isTrue();
    }

    @Test
    public void testContainsAllFromSetCollectionDoesNothing() {
        set = new CaseInsensitiveSet(1);
        final boolean isChanged = set.addAll(createRegularSetWithUpperAndLowerCasedKeys());
        assertThat(isChanged).isTrue();
        assertThat(set).hasSize(1);
        assertThat(set.contains(lower)).isTrue();
        assertThat(set.contains(upper)).isTrue();

        final Set<String> another = new HashSet<String>();
        another.add("OpenIG");
        assertThat(set.containsAll(another)).isFalse();
    }

    /** TODO This one throw a ConcurrentModificationException - The retain method needs to be rewrited with iterator. */
    @Test(enabled = false)
    public void testRetainAllFromSetCollection() {
        set.add(upper);        // Contained in getSet()
        set.add("OpenIG");     // Not contained in the getSet()
        set.add("OpenIG2");    // Not contained in the getSet()
        final boolean isChanged = set.retainAll(createRegularSetWithUpperAndLowerCasedKeys());
        assertThat(isChanged).isTrue();
        assertThat(set).contains(upper);
        assertThat(set).doesNotContain("OpenIG");
        assertThat(set).hasSize(1);
    }

    @Test(enabled = false)
    public void testRetainAllFromSetCollectionDoesNothing() {
        set.add(upper);       // Contained in getSet()
        set.add("OpenIG");    // Not contained in the getSet()
        final boolean isChanged = set.retainAll(createRegularSetWithUpperAndLowerCasedKeys());
        assertThat(isChanged).isFalse();
        assertThat(set).hasSize(1);
        assertThat(set).contains(upper);
    }

    /** TODO This one throw a ConcurrentModificationException -
     * The removeAll method needs to be rewrited with iterator. */
    @Test(enabled = false)
    public void testRemoveAllFromSetCollection() {
        final Collection<String> mySet = createRegularSetWithUpperAndLowerCasedKeys();
        set = new CaseInsensitiveSet();
        set.add(upper);
        set.add(lower);
        final boolean isChanged = set.removeAll(mySet);
        assertThat(isChanged).isTrue();
        assertThat(set).hasSize(0);
        assertThat(set.contains(lower)).isFalse();
        assertThat(set.contains("OpenIG")).isTrue();
        assertThat(set.containsAll(mySet)).isFalse();
    }

    private static Collection<String> createRegularListWithUpperAndLowerCasedKeys() {
        final List<String> sample = new ArrayList<String>();
        sample.add(upper);
        sample.add(lower);
        return sample;
    }

    private static Collection<String> createRegularSetWithUpperAndLowerCasedKeys() {
        final Set<String> hashSet = new HashSet<String>();
        hashSet.add(upper);
        hashSet.add(lower);
        return hashSet;
    }
}
