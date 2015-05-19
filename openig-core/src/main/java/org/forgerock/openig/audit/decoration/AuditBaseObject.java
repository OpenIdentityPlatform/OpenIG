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

import static java.util.Arrays.*;
import static org.forgerock.openig.audit.Tag.*;

import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.openig.audit.AuditEvent;
import org.forgerock.openig.audit.AuditSource;
import org.forgerock.openig.audit.AuditSystem;
import org.forgerock.openig.http.Exchange;

/**
 * Provides a basic implementation for auditable objects.
 * @see AuditFilter
 * @see AuditHandler
 */
abstract class AuditBaseObject {
    private final AuditSource source;
    private final AuditSystem auditSystem;

    protected final Set<String> requestTags;
    protected final Set<String> completedResponseTags;
    protected final Set<String> failedResponseTags;

    public AuditBaseObject(final AuditSource source,
                           final AuditSystem auditSystem,
                           final Set<String> additionalTags) {
        this.source = source;
        this.auditSystem = auditSystem;
        this.requestTags = tags(additionalTags, request.name());
        this.completedResponseTags = tags(additionalTags, response.name(), completed.name());
        this.failedResponseTags = tags(additionalTags, response.name(), exception.name());
    }

    protected void fireAuditEvent(final Exchange exchange, final Set<String> tags) {
        AuditEvent event = new AuditEvent(source, System.currentTimeMillis(), exchange, tags);
        auditSystem.onAuditEvent(event);
    }

    private static Set<String> tags(final Set<String> tags,
                                    final String... others) {
        Set<String> all = new LinkedHashSet<String>(tags);
        all.addAll(asList(others));
        return all;
    }
}
