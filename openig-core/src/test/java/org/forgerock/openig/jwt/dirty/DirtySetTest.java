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

package org.forgerock.openig.jwt.dirty;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashSet;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DirtySetTest {

    @Mock
    private DirtyListener listener;
    private DirtySet<String> dirtySet;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        dirtySet = new DirtySet<>(new HashSet<>(asList("one", "two", "three")), listener);
    }

    @Test
    public void shouldNotifyListenerWhenRemoveIsCalled() throws Exception {
        dirtySet.remove("one");

        verify(listener).onElementsRemoved();
        assertThat(dirtySet).containsOnly("two", "three");
    }

    @Test
    public void shouldNotNotifyListenerWhenRemoveIsCalledWithNoActualChanges() throws Exception {
        dirtySet.remove("four");

        verifyZeroInteractions(listener);
        assertThat(dirtySet).containsOnly("one", "two", "three");
    }

    @Test
    public void shouldNotifyListenerWhenRemoveAllIsCalled() throws Exception {
        dirtySet.removeAll(Arrays.asList("one", "three"));

        verify(listener).onElementsRemoved();
        assertThat(dirtySet).containsOnly("two");
    }

    @Test
    public void shouldNotNotifyListenerWhenRemoveAllIsCalledWithNoActualChanges() throws Exception {
        dirtySet.removeAll(Arrays.asList("four"));

        verifyZeroInteractions(listener);
        assertThat(dirtySet).containsOnly("one", "two", "three");
    }

    @Test
    public void shouldNotifyListenerWhenClearIsCalled() throws Exception {
        dirtySet.clear();

        verify(listener).onElementsRemoved();
        assertThat(dirtySet).isEmpty();
    }

    @Test
    public void shouldNotifyListenerWhenRetainAllIsCalled() throws Exception {
        dirtySet.retainAll(Arrays.asList("three"));

        verify(listener).onElementsRemoved();
        assertThat(dirtySet).containsOnly("three");
    }

    @Test
    public void shouldNotNotifyListenerWhenRetainAllIsCalledWithNoChanges() throws Exception {
        dirtySet.retainAll(Arrays.asList("one", "two", "three"));

        verifyZeroInteractions(listener);
        assertThat(dirtySet).containsOnly("one", "two", "three");
    }
}
