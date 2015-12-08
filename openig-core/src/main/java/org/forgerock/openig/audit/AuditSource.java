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

package org.forgerock.openig.audit;

import static org.forgerock.util.Reject.*;

import org.forgerock.openig.heap.Name;

/**
 * An AuditSource provides information about the {@link AuditEvent} source object (emitter).
 * <p>
 * It includes the following properties:
 * <ul>
 *     <li>{@literal name}: the hierarchically composed {@link Name} of the source heap object (cannot be {@code
 *     null})</li>
 * </ul>
 * Notice that AuditSource is an immutable object.
 * @see AuditEvent
 */
@Deprecated
public class AuditSource {
    private final Name name;

    /**
     * Builds a new AuditSource with the given {@code name}.
     *
     * @param name
     *         heap object {@link Name} that is the source of the notification (cannot be {@code null})
     */
    public AuditSource(final Name name) {
        this.name = checkNotNull(name);
    }

    /**
     * Returns the unique {@link Name} of the heap object notification emitter (cannot be {@code null}).
     *
     * @return the unique {@link Name} of the heap object notification emitter
     */
    public Name getName() {
        return name;
    }
}
