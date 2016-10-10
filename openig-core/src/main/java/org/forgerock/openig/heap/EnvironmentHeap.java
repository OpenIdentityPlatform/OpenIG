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

import org.forgerock.openig.config.Environment;
import org.forgerock.openig.el.Bindings;

/**
 * The root {@link Heap} that includes access to the {@linkplain Environment environment} additional information.
 */
public class EnvironmentHeap extends HeapImpl {
    private final Environment environment;

    /**
     * Builds a new EnvironmentHeap with the given {@code name} and {@code environment}.
     * @param name name of this heap
     * @param environment environment to expose in properties
     */
    public EnvironmentHeap(final Name name, final Environment environment) {
        super(name);
        this.environment = environment;
    }

    @Override
    public Bindings getProperties() {
        Bindings bindings = super.getProperties();
        bindings.bind("openig", environment);
        return bindings;
    }
}
