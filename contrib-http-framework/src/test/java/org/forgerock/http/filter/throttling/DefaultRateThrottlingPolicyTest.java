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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.http.filter.throttling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promises;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DefaultRateThrottlingPolicyTest {

    public static final ThrottlingRate DEFAULT_RATE = new ThrottlingRate(3, duration("1 minute"));

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldRejectNullDefaultRate() throws Exception {
        new DefaultRateThrottlingPolicy(null, mock(ThrottlingPolicy.class));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldRejectNullDataSource() throws Exception {
        new DefaultRateThrottlingPolicy(DEFAULT_RATE, null);
    }

    @Test
    public void shouldReturnDefaultRate() throws Exception {
        ThrottlingPolicy delegate = mock(DefaultRateThrottlingPolicy.class);
        when(delegate.lookup(any(Context.class), any(Request.class)))
                .thenReturn(Promises.<ThrottlingRate, Exception>newResultPromise(null));

        DefaultRateThrottlingPolicy datasource = new DefaultRateThrottlingPolicy(DEFAULT_RATE, delegate);

        ThrottlingRate rate = datasource.lookup(new RootContext(), new Request()).get();

        verify(delegate).lookup(any(Context.class), any(Request.class));
        assertThat(rate).isSameAs(DEFAULT_RATE);
    }

    @Test
    public void shouldNotReturnDefaultRate() throws Exception {
        ThrottlingPolicy delegate = mock(DefaultRateThrottlingPolicy.class);
        ThrottlingRate expected = new ThrottlingRate(42, duration("10 seconds"));
        when(delegate.lookup(any(Context.class), any(Request.class)))
                .thenReturn(Promises.newResultPromise(expected));

        DefaultRateThrottlingPolicy datasource = new DefaultRateThrottlingPolicy(DEFAULT_RATE, delegate);

        ThrottlingRate rate = datasource.lookup(new RootContext(), new Request()).get();

        verify(delegate).lookup(any(Context.class), any(Request.class));
        assertThat(rate).isSameAs(expected);
    }

}
