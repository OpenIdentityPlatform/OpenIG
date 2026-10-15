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

package org.openidentityplatform.openig.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.forgerock.http.protocol.Status;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

@SuppressWarnings("javadoc")
public class ServerEndPointTest {

    private ServerEndPoint endpoint;
    private Session client;

    @BeforeMethod
    public void setUp() throws Exception {
        endpoint = new ServerEndPoint();
        endpoint.principal = mock(Principal.class);
        when(endpoint.principal.authorize()).thenReturn(Status.SWITCHING_PROTOCOLS);
        client = mock(Session.class);
        // No upstream session: the endpoint waits for it (up to ~5 s) and then must give up cleanly
        endpoint.session_upstream = null;
    }

    @Test
    public void shouldCloseClientWithClearReasonWhenUpstreamIsNotConnectedOnTextMessage() throws Exception {
        endpoint.echoTextMessage(client, "hello", true);

        CloseReason reason = closeReasonOf(client);
        assertThat(reason.getCloseCode()).isEqualTo(CloseReason.CloseCodes.CLOSED_ABNORMALLY);
        assertThat(reason.getReasonPhrase())
                .contains("upstream not connected")
                .doesNotContain("NullPointerException");
    }

    @Test
    public void shouldCloseClientWithClearReasonWhenUpstreamIsNotConnectedOnBinaryMessage() throws Exception {
        endpoint.echoBinaryMessage(client, ByteBuffer.wrap(new byte[] { 1, 2, 3 }), true);

        CloseReason reason = closeReasonOf(client);
        assertThat(reason.getCloseCode()).isEqualTo(CloseReason.CloseCodes.CLOSED_ABNORMALLY);
        assertThat(reason.getReasonPhrase())
                .contains("upstream not connected")
                .doesNotContain("NullPointerException");
    }

    @Test
    public void shouldTruncateCloseReasonPhraseToProtocolLimit() {
        // ASCII: cut to 123 bytes
        String ascii = "x".repeat(500);
        assertThat(ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, ascii).getReasonPhrase())
                .isEqualTo("x".repeat(123));
        // Multi-byte: never split a character, never exceed 123 UTF-8 bytes
        String cyrillic = "\u0436".repeat(200);
        String phrase = ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, cyrillic).getReasonPhrase();
        assertThat(phrase.getBytes(StandardCharsets.UTF_8)).hasSize(122);
        assertThat(phrase).isEqualTo("\u0436".repeat(61));
        // Short phrases are left untouched
        assertThat(ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "short").getReasonPhrase())
                .isEqualTo("short");
    }

    private static CloseReason closeReasonOf(Session session) throws Exception {
        ArgumentCaptor<CloseReason> reason = ArgumentCaptor.forClass(CloseReason.class);
        verify(session).close(reason.capture());
        return reason.getValue();
    }
}
