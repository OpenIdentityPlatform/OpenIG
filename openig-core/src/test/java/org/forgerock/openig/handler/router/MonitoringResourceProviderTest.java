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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.http.filter.ResponseHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MonitoringResourceProviderTest {

    @Test
    public void shouldReturnMonitoringDataOnRead() throws Exception {
        // Given
        MonitoringMetrics metrics = new MonitoringMetrics();
        MetricsFilter filter = new MetricsFilter(metrics);
        MonitoringResourceProvider endpoint = new MonitoringResourceProvider(metrics);

        // When
        filter.filter(null, new Request(), new ResponseHandler(Status.OK));
        JsonValue data = endpoint.readInstance(null, null).get().getContent();

        // Then
        assertThat(data.get(ptr("requests/total")).asLong()).isEqualTo(1);
        assertThat(data.get(ptr("requests/active")).asLong()).isEqualTo(0);

        assertThat(data.get(ptr("responses/total")).asLong()).isEqualTo(1);
        assertThat(data.get(ptr("responses/info")).asLong()).isEqualTo(0);
        assertThat(data.get(ptr("responses/success")).asLong()).isEqualTo(1);
        assertThat(data.get(ptr("responses/redirect")).asLong()).isEqualTo(0);
        assertThat(data.get(ptr("responses/clientError")).asLong()).isEqualTo(0);
        assertThat(data.get(ptr("responses/serverError")).asLong()).isEqualTo(0);
        assertThat(data.get(ptr("responses/other")).asLong()).isEqualTo(0);
        assertThat(data.get(ptr("responses/errors")).asLong()).isEqualTo(0);
        assertThat(data.get(ptr("responses/null")).asLong()).isEqualTo(0);

        assertThat(data.get(ptr("throughput/mean")).isNumber()).isTrue();
        assertThat(data.get(ptr("throughput/lastMinute")).isNumber()).isTrue();
        assertThat(data.get(ptr("throughput/last5Minutes")).isNumber()).isTrue();
        assertThat(data.get(ptr("throughput/last15Minutes")).isNumber()).isTrue();

        assertThat(data.get(ptr("responseTime/mean")).isNumber()).isTrue();
        assertThat(data.get(ptr("responseTime/median")).isNumber()).isTrue();
        assertThat(data.get(ptr("responseTime/total")).isNumber()).isTrue();
        assertThat(data.get(ptr("responseTime/standardDeviation")).isNumber()).isTrue();
        assertThat(data.get(ptr("responseTime/percentiles/0.999")).isNumber()).isTrue();
        assertThat(data.get(ptr("responseTime/percentiles/0.9999")).isNumber()).isTrue();
        assertThat(data.get(ptr("responseTime/percentiles/0.99999")).isNumber()).isTrue();
    }

    private static JsonPointer ptr(final String pointer) {
        return new JsonPointer(pointer);
    }
}
