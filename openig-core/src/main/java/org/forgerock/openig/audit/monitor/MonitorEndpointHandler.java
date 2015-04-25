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

package org.forgerock.openig.audit.monitor;

import static org.forgerock.openig.audit.Tag.*;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.audit.AuditEvent;
import org.forgerock.openig.audit.AuditEventListener;
import org.forgerock.openig.audit.ConditionalAuditEventListener;
import org.forgerock.openig.audit.Tag;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.util.EnumUtil;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;


/**
 * Sample statistic endpoint provider that returns JSON-formatted collected statistic values.
 */
public class MonitorEndpointHandler extends GenericHeapObject implements AuditEventListener, Handler {

    private static final Set<String> STANDARD_TAG_NAMES = EnumUtil.names(Tag.class);

    private ConcurrentHashMap<String, TagMetric> metrics = new ConcurrentHashMap<String, TagMetric>();

    @Override
    public Promise<Response, ResponseException> handle(final Context context, final Request request) {
        Response response = new Response();
        response.getEntity().setJson(metrics);
        response.setStatus(200);
        return Promises.newResultPromise(response);
    }

    @Override
    public void onAuditEvent(final AuditEvent event) {
        // Extract the set of additional tags
        Set<String> tags = event.getTags();

        // Manage counter for each of the additional tags, effectively performing correlations
        for (String tag : tags) {
            // Ignore tag if it is a standard one
            if (STANDARD_TAG_NAMES.contains(tag)) {
                continue;
            }
            TagMetric metric = getMetric(tag);
            if (tags.contains(request.name())) {
                metric.active.incrementAndGet();
            }
            if (tags.contains(response.name())) {
                metric.active.decrementAndGet();
                if (tags.contains(completed.name())) {
                    metric.completed.incrementAndGet();
                }
                if (tags.contains(exception.name())) {
                    metric.errors.incrementAndGet();
                }
            }
        }
    }

    private TagMetric getMetric(final String name) {
        TagMetric counter = metrics.get(name);
        if (counter != null) {
            return counter;
        }
        TagMetric newCounter = new TagMetric();
        TagMetric oldCounter = metrics.putIfAbsent(name, newCounter);
        return oldCounter != null ? oldCounter : newCounter;
    }

    /**
     * TagMetric extends a Map implementation to benefit of the natural mapping into a JSON object.
     * Still have 'in progress', 'completed' and 'internal errors' fields for easy programmatic access.
     * TagMetric is thread-safe because no additional fields are put in the structure after creation.
     */
    private static class TagMetric extends LinkedHashMap<String, AtomicLong> {

        public static final long serialVersionUID = 1L;

        final AtomicLong active = new AtomicLong();
        final AtomicLong completed = new AtomicLong();
        final AtomicLong errors = new AtomicLong();

        public TagMetric() {
            put("in progress", active);
            put("completed", completed);
            put("internal errors", errors);
        }
    }

    /**
     * Creates and initializes a MonitorEndpointHandler in a heap environment.
     */
    public static class Heaplet extends ConditionalAuditEventListener.ConditionalListenerHeaplet {
        @Override
        protected AuditEventListener createListener() {
            return new MonitorEndpointHandler();
        }
    }

}
