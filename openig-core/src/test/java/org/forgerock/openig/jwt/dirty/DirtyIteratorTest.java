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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DirtyIteratorTest {

    @Mock
    private DirtyListener listener;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotifyListenerWhenRemoveIsCalled() throws Exception {
        List<String> base = new ArrayList<>(asList("one", "two", "three"));
        DirtyIterator<String> iterator = new DirtyIterator<>(base.iterator(), listener);

        iterator.next();
        iterator.remove();

        verify(listener).onElementsRemoved();
    }

    @Test
    public void shouldDelegateToWrappedIterator() throws Exception {
        List<String> base = new ArrayList<>(asList("one", "two", "three"));
        DirtyIterator<String> iterator = new DirtyIterator<>(base.iterator(), listener);

        assertThat(iterator.next()).isEqualTo("one");
        assertThat(iterator.hasNext()).isTrue();
        iterator.remove();

        assertThat(iterator.next()).isEqualTo("two");
        assertThat(iterator.hasNext()).isTrue();
        iterator.remove();

        assertThat(iterator.next()).isEqualTo("three");
        assertThat(iterator.hasNext()).isFalse();
        iterator.remove();

        verify(listener, times(3)).onElementsRemoved();
        assertThat(base).isEmpty();
    }
}
