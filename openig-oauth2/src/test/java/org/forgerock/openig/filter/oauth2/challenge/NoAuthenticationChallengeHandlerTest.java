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

package org.forgerock.openig.filter.oauth2.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class NoAuthenticationChallengeHandlerTest {
    @Test
    public void should401WithRealmOnlyChallenge() throws Exception {
        NoAuthenticationChallengeHandler handler = new NoAuthenticationChallengeHandler("test");

        Response response = handler.handle(null, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.UNAUTHORIZED);

        List<String> authenticates = response.getHeaders().get("WWW-Authenticate").getValues();
        assertThat(authenticates).containsOnly("Bearer realm=\"test\"");

    }
}
