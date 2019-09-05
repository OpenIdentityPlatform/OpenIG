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

package org.forgerock.openig.handler.saml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ResponseAdapterTest {

    @Mock
    private HttpServletResponse delegate;
    private ByteArrayOutputStream stream;
    private ServletOutputStream servletStream;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(delegate.getWriter()).thenThrow(new IOException("can't use delegate.getWriter()"));

        stream = new ByteArrayOutputStream();
        servletStream = spy(new ServletOutputStream() {
            @Override
            public void write(final int b) throws IOException {
                stream.write(b);
            }

			@Override
			public boolean isReady() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void setWriteListener(WriteListener writeListener) {
				// TODO Auto-generated method stub
				
			}
        });
        when(delegate.getOutputStream()).thenReturn(servletStream);
        when(delegate.getCharacterEncoding()).thenReturn("UTF-8");
    }

    @Test
    public void shouldWriteInOutputStream() throws Exception {
        ResponseAdapter adapter = new ResponseAdapter(delegate);

        PrintWriter writer = adapter.getWriter();

        // Should always returns the same instance
        assertThat(writer).isNotNull()
                          .isSameAs(adapter.getWriter());

        // Writer prints in the stream + flushing the writer flushes the stream
        writer.print("Hello World");
        writer.flush();

        verify(servletStream).flush();
        assertThat(new String(stream.toByteArray(), "UTF-8")).isEqualTo("Hello World");
    }
}
