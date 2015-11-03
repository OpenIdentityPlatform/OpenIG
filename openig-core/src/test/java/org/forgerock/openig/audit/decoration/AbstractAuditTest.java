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

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Sets.newLinkedHashSet;

import org.forgerock.openig.audit.AuditEvent;
import org.forgerock.openig.audit.AuditSource;
import org.forgerock.openig.audit.AuditSystem;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.Context;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

@SuppressWarnings("javadoc")
public abstract class AbstractAuditTest {
    @Captor
    protected ArgumentCaptor<AuditEvent> captor;
    @Mock
    protected AuditSystem auditSystem;
    protected AuditSource source;

    @DataProvider
    public static Object[][] nullOrEmpty() {
        return new Object[][] {
            { singleton("") },
            { singleton(null) },
            { newLinkedHashSet(null, "") } };
    }

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        source = new AuditSource(Name.of("Test"));
    }

    protected void assertThatEventIncludes(final AuditEvent event, final Context context, final String... tags) {
        assertThat(event.getTags()).containsOnly(tags);
        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getData().get("context")).isSameAs(context);
    }
}
