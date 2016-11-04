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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.util.Collections;

import org.forgerock.services.context.RootContext;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PolicyDecisionContextTest {

    @Test
    public void shouldBeNamedPolicyDecision() throws Exception {
        PolicyDecisionContext context = newPolicyDecisionContext();
        assertThat(context.getContextName()).isEqualTo("policyDecision");
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void shouldReturnUnmodifiableAdvices() throws Exception {
        PolicyDecisionContext context = newPolicyDecisionContext();
        context.getAdvices().put("key", Collections.<String>emptyList());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void shouldReturnUnmodifiableAttributes() throws Exception {
        PolicyDecisionContext context = newPolicyDecisionContext();
        context.getAttributes().put("key", Collections.<String>emptyList());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void shouldReturnUnmodifiableJsonAttributes() throws Exception {
        PolicyDecisionContext context = newPolicyDecisionContext();
        context.getJsonAttributes().put("key", "don't care about type");
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void shouldReturnUnmodifiableOfJsonAdvices() throws Exception {
        PolicyDecisionContext context = newPolicyDecisionContext();
        context.getJsonAdvices().put("key", "don't care about type");
    }

    private static PolicyDecisionContext newPolicyDecisionContext() {
        return new PolicyDecisionContext(new RootContext(),
                                         json(object()),
                                         json(object()));
    }
}
