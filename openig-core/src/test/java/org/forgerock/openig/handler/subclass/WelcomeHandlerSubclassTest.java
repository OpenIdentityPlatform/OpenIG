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

package org.forgerock.openig.handler.subclass;

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.http.io.IO;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.handler.WelcomeHandler;
import org.testng.annotations.Test;

/**
 * Lives in a different package on purpose: the welcome page must be resolved relative to
 * {@link WelcomeHandler}, not relative to whatever class extends it.
 */
@SuppressWarnings("javadoc")
public class WelcomeHandlerSubclassTest {

    @Test
    public void shouldServeWelcomePageFromSubclassInAnotherPackage() throws Exception {
        final WelcomeHandler handler = new WelcomeHandler(IO.newTemporaryStorage()) { };
        Request request = new Request();
        request.setMethod("GET");
        request.setUri("http://example.com/");

        Response response = handler.handle(null, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.OK);
        assertThat(response.getEntity().getString()).contains("<html");
        response.close();
    }
}
