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

package org.forgerock.openig.jwt.dirty;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DirtyCollectionTest {

    @Mock
    private Collection<String> delegate;

    @Mock
    private DirtyListener listener;
    private DirtyCollection<String> collection;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        collection = new DirtyCollection<String>(new HashSet<String>(asList("one", "two", "three")), listener);
    }

    @Test
    public void shouldNotifyListenerWhenRemoveIsCalled() throws Exception {
        collection.remove("one");

        verify(listener).onElementsRemoved();
        assertThat(collection).containsOnly("two", "three");
    }

    @Test
    public void shouldNotifyListenerWhenRemoveAllIsCalled() throws Exception {
        collection.removeAll(Arrays.asList("one", "three"));

        verify(listener).onElementsRemoved();
        assertThat(collection).containsOnly("two");
    }

    @Test
    public void shouldNotNotifyListenerWhenRemoveAllIsCalledWithNoActualChanges() throws Exception {
        collection.removeAll(Arrays.asList("four"));

        verifyZeroInteractions(listener);
        assertThat(collection).containsOnly("one", "two", "three");
    }

    @Test
    public void shouldNotifyListenerWhenClearIsCalled() throws Exception {
        collection.clear();

        verify(listener).onElementsRemoved();
        assertThat(collection).isEmpty();
    }

    @Test
    public void shouldNotifyListenerWhenRetainAllIsCalled() throws Exception {
        collection.retainAll(Arrays.asList("three"));

        verify(listener).onElementsRemoved();
        assertThat(collection).containsOnly("three");
    }

    @Test
    public void shouldNotNotifyListenerWhenRetainAllIsCalledWithNoChanges() throws Exception {
        collection.retainAll(Arrays.asList("one", "two", "three"));

        verifyZeroInteractions(listener);
        assertThat(collection).containsOnly("one", "two", "three");
    }

    @Test
    public void shouldDelegateAllMethodsToDelegatee() throws Exception {
        DirtyCollection<String> collection = new DirtyCollection<String>(delegate, listener);

        collection.clear();
        collection.isEmpty();
        collection.remove(null);
        collection.removeAll(null);
        collection.add(null);
        collection.addAll(null);
        collection.contains(null);
        collection.containsAll(null);
        collection.retainAll(null);
        collection.iterator();
        collection.size();
        collection.toArray();
        collection.toArray(null);

        verify(delegate).clear();
        verify(delegate).isEmpty();
        verify(delegate).remove(null);
        verify(delegate).removeAll(null);
        verify(delegate).add(null);
        verify(delegate).addAll(null);
        verify(delegate).contains(null);
        verify(delegate).containsAll(null);
        verify(delegate).retainAll(null);
        verify(delegate).iterator();
        verify(delegate).size();
        verify(delegate).toArray();
        verify(delegate).toArray(null);
    }
}
