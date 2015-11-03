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

package org.forgerock.openig.audit;

import static java.util.Collections.*;
import static org.forgerock.util.Reject.*;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.forgerock.openig.el.Bindings;

/**
 * An AuditEvent represents a point in time during the processing of a Request.
 * <p>
 * Instances of this class are <b>not thread-safe</b>: any filter can possibly update the {@code data} while
 * processing the audit event. Special care must be taken when dealing with {@link org.forgerock.http.protocol.Request},
 * {@link org.forgerock.http.protocol.Response} and {@link org.forgerock.services.context.Context}.
 * <p>
 * The {@literal source} property helps to identify the object that emitted the notification.
 * The {@literal timestamp} property gives a time marker to keep events organized in a
 * sequential manner (expressed in milliseconds).
 * The {@literal data} property gives a pointer to the captured {@code context} (never {@code null}), {@code request}
 * (never {@code null}) and {@code response} (may be {@code null} when audit happens on the request flow). There is
 * no way to guarantee, if the notification is processed in an asynchronous way, that the bindings content was not
 * modified in the meantime.
 * The {@literal tags} property helps to qualify this notification (no duplicated values).
 */
public final class AuditEvent {
    private final AuditSource source;
    private final long timestamp;
    private final Map<String, Object> data;
    private final Set<String> tags = new LinkedHashSet<>();

    /**
     * Builds a new AuditEvent with provided values.
     *
     * @param source
     *         source of the event (never {@code null})
     * @param timestamp
     *         creation date of the notification (expressed in milliseconds)
     * @param bindings
     *         Exposed data (never {@code null})
     * @param tags
     *         qualifiers (never {@code null})
     */
    public AuditEvent(final AuditSource source,
                      final long timestamp,
                      final Bindings bindings,
                      final Collection<String> tags) {
        this.source = checkNotNull(source);
        this.timestamp = timestamp;
        this.data = checkNotNull(bindings).asMap();
        this.tags.addAll(checkNotNull(tags));
    }

    /**
     * Returns the source of the audit event (never {@code null}).
     *
     * @return the source of the audit event (never {@code null}).
     */
    public AuditSource getSource() {
        return source;
    }

    /**
     * Returns the timestamp of this event (expressed in milliseconds).
     *
     * @return the timestamp of this event (expressed in milliseconds).
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the captured {@code data} (never {@code null}).
     * Notice that this is a pointer to the being processed data (live objects, not copy), so, if this event
     * is processed asynchronously, the data content may have changed without notice.
     *
     * @return the captured {@code data} (never {@code null}).
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Returns an immutable set of event's qualifiers (never {@code null}).
     *
     * @return an immutable set of event's qualifiers (never {@code null}).
     */
    public Set<String> getTags() {
        return unmodifiableSet(tags);
    }
}
