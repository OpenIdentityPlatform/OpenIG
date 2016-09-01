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
package org.forgerock.openig.decoration.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AbstractDecoratorTest {

    @Test
    public void shouldCreateLogger() {
        final Context context = mock(Context.class);
        when(context.getName()).thenReturn(Name.of("myDecoratedObjectName"));

        final AbstractDecorator decorator = new ExtendedDecorator("myDecoratorName");
        assertThat(decorator.getLogger(context).getName()).isEqualTo(
                ExtendedDecorator.class.getName() + ".myDecoratorName.myDecoratedObjectName");
    }

    private static class ExtendedDecorator extends AbstractDecorator {

        ExtendedDecorator(String name) {
            super(name);
        }

        @Override
        public boolean accepts(Class<?> type) {
            return false;
        }

        @Override
        public Object decorate(Object delegate, JsonValue decoratorConfig, Context context) throws HeapException {
            return null;
        }
    }
}
