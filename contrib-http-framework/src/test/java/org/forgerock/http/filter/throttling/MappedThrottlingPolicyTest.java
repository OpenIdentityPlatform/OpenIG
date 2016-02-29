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

package org.forgerock.http.filter.throttling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.duration;

import java.util.Collections;
import java.util.Map;

import org.forgerock.http.ContextAndRequest;
import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MappedThrottlingPolicyTest {

    public static final ThrottlingRate DEFAULT_RATE = new ThrottlingRate(3, duration("1 minute"));

    @Test
    public void shouldReturnMatchingRateOrDefaultRate() throws Exception {
        ThrottlingRate expected = new ThrottlingRate(42, duration("1 hour"));
        Map<String, ThrottlingRate> rates = Collections.singletonMap("foo", expected);
        MappedThrottlingPolicy datasource = new MappedThrottlingPolicy(throttlingRateFunction(),
                                                                       rates,
                                                                       DEFAULT_RATE);

        ThrottlingRate rate;
        rate = datasource.lookup(new RootContext(), request("foo")).get();
        assertThat(rate).isSameAs(expected);

        rate = datasource.lookup(new RootContext(), request("bar")).get();
        assertThat(rate).isSameAs(DEFAULT_RATE);

        rate = datasource.lookup(new RootContext(), request(null)).get();
        assertThat(rate).isSameAs(DEFAULT_RATE);
    }

    private AsyncFunction<ContextAndRequest, String, Exception> throttlingRateFunction() {
        return new AsyncFunction<ContextAndRequest, String, Exception>() {
            @Override
            public Promise<String, Exception> apply(ContextAndRequest flow) {
                return newResultPromise(flow.getRequest().getHeaders().getFirst("ThrottlingRate"));
            }
        };
    }

    private static Request request(String value) {
        Request request = new Request();
        request.getHeaders().put("ThrottlingRate", value);
        return request;
    }

}
