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

package org.forgerock.openig.openam;

import static java.util.Collections.unmodifiableMap;
import static org.forgerock.util.Reject.checkNotNull;

import java.util.List;
import java.util.Map;

import org.forgerock.json.JsonValue;
import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;

/**
 * A {@link PolicyDecisionContext} convey policy decision information to downstream filters and handlers.
 */
public class PolicyDecisionContext extends AbstractContext {

    private final JsonValue attributes;
    private final JsonValue advices;

    PolicyDecisionContext(final Context parent,
                          final JsonValue attributes,
                          final JsonValue advices) {
        super(parent, "policyDecision");

        this.attributes = new JsonValue(asUnmodifiableMapCopy(attributes), attributes.getPointer());
        this.advices = new JsonValue(asUnmodifiableMapCopy(advices), advices.getPointer());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> asUnmodifiableMapCopy(final JsonValue object) {
        return unmodifiableMap((Map<String, List<String>>) checkNotNull(object).required()
                                                                               .expect(Map.class)
                                                                               .copy()
                                                                               .getObject());
    }

    /**
     * Returns the unmodifiable map of {@literal attributes} provided in the policy decision (can be empty, but
     * never {@code null}).
     * @return the map of attributes provided in the policy decision
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getAttributes() {
        return (Map<String, List<String>>) attributes.getObject();
    }

    /**
     * Returns the unmodifiable {@literal attributes} entry in the policy decision (never {@code null}).
     *
     * <p>The returned JsonValue wraps a {@code Map<String, List<String>>} just like:
     * <pre>
     *     {@code {
     *         "dn": [ "uid=bjensen,dc=example,dc=com" ],
     *         "emails": [ "bjensen@example.com", "jensen@acme.org" ]
     *     }
     *     }
     * </pre>
     * @return the unmodifiable {@literal attributes} entry in the policy decision (never {@code null}).
     */
    public JsonValue getJsonAttributes() {
        return attributes;
    }

    /**
     * Returns the unmodifiable map of {@literal advices} provided in the policy decision (can be empty, but
     * never {@code null}).
     * @return the map of advices provided in the policy decision
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getAdvices() {
        return (Map<String, List<String>>) advices.getObject();
    }

    /**
     * Returns the unmodifiable {@literal advices} entry in the policy decision (never {@code null}).
     *
     * <p>The returned JsonValue wraps a {@code Map<String, List<String>>} just like:
     * <pre>
     *     {@code {
     *         "AuthLevelConditionAdvice": [ "3" ]
     *     }
     *     }
     * </pre>
     * @return the unmodifiable {@literal advices} entry in the policy decision (never {@code null}).
     */
    public JsonValue getJsonAdvices() {
        return advices;
    }
}
