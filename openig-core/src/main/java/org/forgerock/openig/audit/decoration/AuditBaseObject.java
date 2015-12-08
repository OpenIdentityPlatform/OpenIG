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
package org.forgerock.openig.audit.decoration;

import static java.util.Arrays.asList;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.openig.el.Bindings;

/**
 * Provides a basic implementation for auditable objects.
 * @see AuditFilter
 * @see AuditHandler
 */
@Deprecated
abstract class AuditBaseObject {
    private final org.forgerock.openig.audit.AuditSource source;
    private final org.forgerock.openig.audit.AuditSystem auditSystem;

    protected final Set<String> requestTags;
    protected final Set<String> completedResponseTags;
    protected final Set<String> failedResponseTags;

    public AuditBaseObject(final org.forgerock.openig.audit.AuditSource source,
                           final org.forgerock.openig.audit.AuditSystem auditSystem,
                           final Set<String> additionalTags) {
        this.source = source;
        this.auditSystem = auditSystem;
        this.requestTags = tags(additionalTags, org.forgerock.openig.audit.Tag.request.name());
        this.completedResponseTags = tags(additionalTags,
                                          org.forgerock.openig.audit.Tag.response.name(),
                                          org.forgerock.openig.audit.Tag.completed.name());
        this.failedResponseTags = tags(additionalTags,
                                       org.forgerock.openig.audit.Tag.response.name(),
                                       org.forgerock.openig.audit.Tag.exception.name());
    }

    protected void fireAuditEvent(final Bindings bindings, final Set<String> tags) {
        org.forgerock.openig.audit.AuditEvent event =
                new org.forgerock.openig.audit.AuditEvent(source, System.currentTimeMillis(), bindings, tags);
        auditSystem.onAuditEvent(event);
    }

    private static Set<String> tags(final Set<String> tags,
                                    final String... others) {
        final Set<String> all = new LinkedHashSet<>(tags);
        all.addAll(asList(others));
        all.remove("");
        all.remove(null);
        return all;
    }
}
