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

package org.forgerock.openig.audit.decoration;

import static org.forgerock.util.Reject.*;

import java.util.Set;
import java.util.TreeSet;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.audit.AuditEvent;
import org.forgerock.openig.audit.AuditSource;
import org.forgerock.openig.audit.AuditSystem;
import org.forgerock.openig.audit.Tag;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.helper.AbstractHandlerAndFilterDecorator;
import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.heap.HeapException;

/**
 * The audit decorator can decorate both {@link Handler} and {@link Filter} instances.
 * It triggers notifications ({@link AuditEvent}) to an {@link AuditSystem} sink.
 * <p>
 * Each {@link AuditEvent} includes a source marker that will indicate that the event comes
 * from the decorated component.
 * <p>
 * Each notification includes a set of <i>tags</i> that helps the notification receiver to filter the
 * events with simple matching rules. Here is the list of built-in tags:
 * <ul>
 *     <li>{@link Tag#request}: The event happens before the delegate {@link Filter}/{@link Handler} is called</li>
 *     <li>{@link Tag#response}: The event happens after the delegate {@link Filter}/{@link Handler} was called</li>
 *     <li>{@link Tag#completed}: The event happens when the exchange has been completely handled <b>successfully</b>
 *     by the processing unit (always complements a {@link Tag#response} tag)</li>
 *     <li>{@link Tag#exception}: The event happens when the exchange has been handled with <b>errors</b>
 *     by the processing unit (always complements a {@link Tag#response} tag). Notice that this does not indicate that
 *     the source heap object is the origin of the failure (it may or may not have thrown the exception itself).</li>
 * </ul>
 * <p>
 * The user can add extra tags to the list of tags that decorates the notification, in order to help
 * notification qualification:
 * <pre>
 *     {@code
 *         "audit": "route-#1"  // add a single tag to the decorated component
 *         "audit": [ "super-tag", "route-#2" ] // add all of theses tags
 *         "audit": boolean, object, ... // any other format will be ignored
 *     }
 * </pre>
 * <p>
 * Notice that the attribute name in the decorated object <b>has to be</b> the same as the decorator
 * heap object name ({@code audit} in our example).
 * <p>
 * A default {@literal audit} decorator is automatically created when OpenIG starts.
 *
 * @see Tag
 */
public class AuditDecorator extends AbstractHandlerAndFilterDecorator {

    /**
     * Key to retrieve a {@link AuditDecorator} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String AUDIT_HEAP_KEY = "audit";

    private final AuditSystem auditSystem;

    /**
     * Builds a new AuditDecorator that will send events to the provided AuditSystem.
     *
     * @param auditSystem
     *         AuditSystem reference (cannot be {@code null})
     */
    public AuditDecorator(final AuditSystem auditSystem) {
        this.auditSystem = checkNotNull(auditSystem);
    }

    @Override
    protected Filter decorateFilter(final Filter delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        return new AuditFilter(auditSystem, source(context, delegate), delegate, getAdditionalTags(decoratorConfig));
    }

    @Override
    protected Handler decorateHandler(final Handler delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        return new AuditHandler(auditSystem, source(context, delegate), delegate, getAdditionalTags(decoratorConfig));
    }

    private static AuditSource source(final Context context, final Object delegate) {
        return new AuditSource(context.getName());
    }

    private static Set<String> getAdditionalTags(final JsonValue config) {
        Set<String> tags = new TreeSet<String>();
        if (config.isString()) {
            tags.add(config.asString());
        } else if (config.isList()) {
            tags.addAll(config.asSet(String.class));
        }
        // otherwise, returns an empty set
        return tags;
    }
}
