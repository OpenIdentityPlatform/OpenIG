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

package org.forgerock.openig.audit.monitor;

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.openig.audit.AuditEvent;
import org.forgerock.openig.audit.AuditSource;
import org.forgerock.openig.audit.Tag;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.util.EnumUtil;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MonitorEndpointHandlerTest {

    @DataProvider
    public static Object[][] standardTags() {
        Set<String> names = EnumUtil.names(Tag.class);
        Object[][] values = new Object[4][1];
        int index = 0;
        for (String name : names) {
            values[index++][0] = name;
        }
        return values;
    }

    @Test(dataProvider = "standardTags")
    public void shouldNotCountWhenNoUserTag(String tag) throws Exception {
        MonitorEndpointHandler monitor = new MonitorEndpointHandler();
        monitor.onAuditEvent(buildAuditEvent(tag));

        Exchange exchange = new Exchange();
        monitor.handle(exchange);

        assertThat(getJsonObject(exchange)).isEmpty();
    }

    @Test
    public void shouldIncrementCompletedCounter() throws Exception {
        MonitorEndpointHandler monitor = new MonitorEndpointHandler();
        monitor.onAuditEvent(buildAuditEvent("my-tag", "response", "completed"));

        Exchange exchange = new Exchange();
        monitor.handle(exchange);

        Map<String, AtomicLong> tag = getJsonObject(exchange).get("my-tag");
        assertThat(tag.get("completed").get()).isEqualTo(1L);
        assertThat(tag.get("failed").get()).isEqualTo(0L);
    }

    @Test
    public void shouldIncrementFailedCounter() throws Exception {
        MonitorEndpointHandler monitor = new MonitorEndpointHandler();
        monitor.onAuditEvent(buildAuditEvent("my-tag", "response", "exception"));

        Exchange exchange = new Exchange();
        monitor.handle(exchange);

        Map<String, AtomicLong> tag = getJsonObject(exchange).get("my-tag");
        assertThat(tag.get("completed").get()).isEqualTo(0L);
        assertThat(tag.get("failed").get()).isEqualTo(1L);
    }

    @Test
    public void shouldIncrementAndDecrementActiveCounter() throws Exception {
        MonitorEndpointHandler monitor = new MonitorEndpointHandler();
        monitor.onAuditEvent(buildAuditEvent("my-tag", "request"));

        Exchange exchange = new Exchange();
        monitor.handle(exchange);

        Map<String, AtomicLong> tag = getJsonObject(exchange).get("my-tag");
        assertThat(tag.get("active").get()).isEqualTo(1L);
        assertThat(tag.get("completed").get()).isEqualTo(0L);
        assertThat(tag.get("failed").get()).isEqualTo(0L);

        monitor.onAuditEvent(buildAuditEvent("my-tag", "response"));

        Exchange exchange2 = new Exchange();
        monitor.handle(exchange2);

        Map<String, AtomicLong> tag2 = getJsonObject(exchange2).get("my-tag");
        assertThat(tag2.get("active").get()).isEqualTo(0L);
        assertThat(tag2.get("completed").get()).isEqualTo(0L);
        assertThat(tag2.get("failed").get()).isEqualTo(0L);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, AtomicLong>> getJsonObject(final Exchange exchange)
            throws IOException {
        return (Map) exchange.response.getEntity().getJson();
    }

    private static AuditEvent buildAuditEvent(final String... tags) {
        return new AuditEvent(new AuditSource(Name.of("source")),
                              System.currentTimeMillis(),
                              new Exchange(),
                              asList(tags));
    }
}
