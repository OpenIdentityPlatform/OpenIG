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
 * Copyright 2026 3A Systems, LLC.
 */

package org.openidentityplatform.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.HeapUtilsTest;
import org.forgerock.openig.heap.Name;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ICAPFilterTest {

    @Test
    public void shouldConfigureTimeouts() throws Exception {
        ICAPFilter filter = (ICAPFilter) new ICAPFilter.Heaplet().create(
                Name.of("icap"),
                json(object(field("server", "icap://localhost:1344"),
                            field("connect_timeout", "1000"),
                            field("read_timeout", "2000"))),
                HeapUtilsTest.buildDefaultHeap());

        assertThat(filter.icap.getConnectTimeout()).isEqualTo(1000);
        assertThat(filter.icap.getReadTimeout()).isEqualTo(2000);
    }

    @DataProvider
    public static Object[][] timeoutFields() {
        return new Object[][] { { "connect_timeout" }, { "read_timeout" } };
    }

    @Test(dataProvider = "timeoutFields")
    public void shouldRejectNonNumericTimeoutNamingTheField(String name) {
        assertThatThrownBy(() -> new ICAPFilter.Heaplet().create(
                Name.of("icap"),
                json(object(field("server", "icap://localhost:1344"),
                            field(name, "soon"))),
                HeapUtilsTest.buildDefaultHeap()))
                .isInstanceOf(JsonValueException.class)
                .hasMessageContaining(name)
                .hasMessageContaining("soon");
    }
}
