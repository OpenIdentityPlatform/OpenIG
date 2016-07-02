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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.doc;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.handler.StaticResponseHandler;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SampleFilterTest {

    @Test
    public void onRequest() throws Exception {
        SampleFilter filter = new SampleFilter();
        filter.name  = "X-Greeting";
        filter.value = "Hello world";

        Context context = new RootContext();
        Request request = new Request();

        Handler chain = Handlers.chainOf(new StaticResponseHandler(Status.OK), singletonList((Filter) filter));

        Response response = chain.handle(context, request).get();

        assertThat(request.getHeaders().get(filter.name).getValues())
                .containsOnly("Hello world");
        assertThat(response.getHeaders().get(filter.name).getValues())
                .containsOnly("Hello world");
    }
}
