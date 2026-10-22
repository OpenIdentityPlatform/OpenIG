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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.forgerock.http.protocol.Status;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.websocket.CloseReason;
import jakarta.websocket.RemoteEndpoint;
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
    public void shouldTruncateLongUpstreamErrorWhenClosingClient() throws Exception {
        // Connected upstream that fails with a message longer than a close frame can carry
        Session upstream = mock(Session.class);
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        when(upstream.isOpen()).thenReturn(true);
        when(upstream.getBasicRemote()).thenReturn(remote);
        doThrow(new IOException("x".repeat(200))).when(remote).sendText("hello", true);
        endpoint.session_upstream = upstream;

        endpoint.echoTextMessage(client, "hello", true);

        CloseReason reason = closeReasonOf(client);
        assertThat(reason.getCloseCode()).isEqualTo(CloseReason.CloseCodes.CLOSED_ABNORMALLY);
        assertThat(reason.getReasonPhrase())
                .startsWith("client message error: java.io.IOException: x")
                .endsWith("\u2026");
        assertThat(reason.getReasonPhrase().getBytes(StandardCharsets.UTF_8)).hasSize(123);
        verify(upstream).close(reason);
        assertThat(endpoint.session_upstream).isNull();
    }

    @Test
    public void shouldCloseRevokedClientWithoutWaitingForUpstreamOnTextMessage() throws Exception {
        // Access is checked before the endpoint waits for the upstream
        when(endpoint.principal.authorize()).thenReturn(Status.FORBIDDEN);

        endpoint.echoTextMessage(client, "hello", true);

        assertThat(closeReasonOf(client).getReasonPhrase()).isEqualTo("client message error: access revoked");
    }

    @Test
    public void shouldCloseRevokedClientWithoutWaitingForUpstreamOnBinaryMessage() throws Exception {
        when(endpoint.principal.authorize()).thenReturn(Status.FORBIDDEN);

        endpoint.echoBinaryMessage(client, ByteBuffer.wrap(new byte[] { 1, 2, 3 }), true);

        assertThat(closeReasonOf(client).getReasonPhrase()).isEqualTo("client message error: access revoked");
    }

    @Test
    public void shouldCloseUpstreamWithClientErrorOnError() throws Throwable {
        Session upstream = mock(Session.class);
        endpoint.session_upstream = upstream;
        // Tomcat runs @OnClose on the calling thread, from inside close()
        doAnswer(invocation -> {
            endpoint.end(client, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, ""));
            return null;
        }).when(client).close();

        endpoint.onError(client, new IOException("broken pipe"));

        CloseReason reason = closeReasonOf(upstream);
        assertThat(reason.getCloseCode()).isEqualTo(CloseReason.CloseCodes.CLOSED_ABNORMALLY);
        assertThat(reason.getReasonPhrase()).isEqualTo("client error: java.io.IOException: broken pipe");
        verify(client).close();
        assertThat(endpoint.session_upstream).isNull();
    }

    @Test
    public void shouldCloseStaleUpstreamWhenItIsNotOpen() throws Exception {
        // Upstream session is set but already closed: the endpoint waits for it (up to ~5 s), then drops it
        Session upstream = mock(Session.class);
        when(upstream.isOpen()).thenReturn(false);
        endpoint.session_upstream = upstream;

        endpoint.echoTextMessage(client, "hello", true);

        assertThat(closeReasonOf(client).getReasonPhrase()).contains("upstream not connected");
        verify(upstream).close(any(CloseReason.class));
        assertThat(endpoint.session_upstream).isNull();
    }

    @Test
    public void shouldTruncateCloseReasonPhraseToProtocolLimit() {
        String ellipsis = "\u2026";
        // ASCII: up to 123 bytes untouched, longer cut to 120 bytes plus the 3-byte ellipsis
        assertThat(ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "x".repeat(123)).getReasonPhrase())
                .isEqualTo("x".repeat(123));
        assertThat(ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "x".repeat(500)).getReasonPhrase())
                .isEqualTo("x".repeat(120) + ellipsis);
        // Multi-byte: never split a character, never exceed 123 UTF-8 bytes
        String cyrillic = "\u0436".repeat(200);
        String phrase = ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, cyrillic).getReasonPhrase();
        assertThat(phrase.getBytes(StandardCharsets.UTF_8)).hasSize(123);
        assertThat(phrase).isEqualTo("\u0436".repeat(60) + ellipsis);
        // Surrogate pairs: never leave the high half of a pair at the end of the phrase
        String smile = "\uD83D\uDE00";
        assertThat(ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "x" + smile.repeat(100)).getReasonPhrase())
                .isEqualTo("x" + smile.repeat(29) + ellipsis);
        assertThat(ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "a".repeat(119) + smile.repeat(5)).getReasonPhrase())
                .isEqualTo("a".repeat(119) + ellipsis);
        assertThat(ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "\uD83D").getReasonPhrase())
                .isEmpty();
        // Short phrases are left untouched
        assertThat(ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "short").getReasonPhrase())
                .isEqualTo("short");
        assertThat(ServerEndPoint.closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "").getReasonPhrase())
                .isEmpty();
    }

    private static CloseReason closeReasonOf(Session session) throws Exception {
        ArgumentCaptor<CloseReason> reason = ArgumentCaptor.forClass(CloseReason.class);
        verify(session).close(reason.capture());
        return reason.getValue();
    }
}
