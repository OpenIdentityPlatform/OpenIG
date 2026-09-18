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
 * Copyright 2022-2026 3A Systems LLC.
 */

package org.openidentityplatform.openig.websocket;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import org.forgerock.http.protocol.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.ClientEndpointConfig.Builder;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


@ServerEndpoint(value = "/{level1}",configurator = Configurator.class)
public class ServerEndPoint {
	static Logger logger=LoggerFactory.getLogger(ServerEndPoint.class); 
	
	Principal principal=null;
	volatile Session session_upstream=null;

	/** A close frame reason phrase is limited to 123 UTF-8 bytes; {@link CloseReason} rejects longer ones. */
	static final int MAX_REASON_PHRASE_BYTES = 123;

	/** Builds a {@link CloseReason}, truncating the phrase so that it never exceeds the protocol limit. */
	static CloseReason closeReason(CloseReason.CloseCode code, String phrase) {
		String reason = phrase.length() > MAX_REASON_PHRASE_BYTES ? phrase.substring(0, MAX_REASON_PHRASE_BYTES) : phrase;
		while (reason.getBytes(StandardCharsets.UTF_8).length > MAX_REASON_PHRASE_BYTES) {
			reason = reason.substring(0, reason.length() - 1);
		}
		return new CloseReason(code, reason);
	}

	/** Waits (up to ~5 s) for the upstream session to be connected and returns it. */
	private Session upstream() throws IOException, InterruptedException {
		int ct=0;
		while ((ct++<5000)&&(session_upstream==null || !session_upstream.isOpen())) {
			Thread.sleep(1);
		}
		final Session upstream=session_upstream;
		if (upstream==null || !upstream.isOpen()) {
			throw new IOException("upstream not connected");
		}
		return upstream;
	}

	/** Closes the upstream session (if any) with the given reason and forgets it. */
	private void closeUpstream(CloseReason reason) {
		final Session upstream=session_upstream;
		session_upstream=null;
		if (upstream!=null) {
			try {
				upstream.close(reason);
			} catch (Throwable e1) {}
		}
	}

	/** Closes both the client and the upstream sessions abnormally with the given reason. */
	private void closeAbnormally(Session session_client, String phrase) {
		final CloseReason reason=closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, phrase);
		try {
			session_client.close(reason);
		} catch (Throwable e1) {}
		closeUpstream(reason);
	}
	
	public void authorize() throws Exception {
		if (principal==null || principal.authorize().getCode()!=101) {
			throw new Exception() {
				private static final long serialVersionUID = 1L;

				@Override
				public String toString() {
					return "access revoked";
				}
			};
		}
	}
    @OnOpen
    public void start(Session session_client, EndpointConfig config) {
    	principal=((Principal)session_client.getUserPrincipal());
    	logger.debug("client open {}: {}",session_client.getRequestURI(),session_client.getId());

    	session_client.setMaxTextMessageBufferSize(1024*1024);
    	session_client.setMaxIdleTimeout(30*60*1000);
    	
    	try {
        	final Builder configBuilder = ClientEndpointConfig.Builder.create();
        	configBuilder.configurator(new ClientEndpointConfig.Configurator() {
				@Override
        	    public void beforeRequest(Map<String, List<String>> headers) {
					for (Entry<String,Object> entry : principal.request.getHeaders().entrySet()) {
						if (entry.getKey().toLowerCase().startsWith("cookie")
								||entry.getKey().toLowerCase().startsWith("x-")
								||entry.getKey().equalsIgnoreCase("origin")
								||entry.getKey().equalsIgnoreCase("Authorization")
								||entry.getKey().toLowerCase().startsWith("sec-websocket-")) {
							headers.put(entry.getKey(), ((Header)entry.getValue()).getValues());
						}
					}
        	    }
        	});
           
        	this.session_upstream = ContainerProvider.getWebSocketContainer().connectToServer(
            		new Endpoint() {

						@Override
						public void onOpen(Session session_upstream, EndpointConfig config) {
							logger.debug("upstream open {} {}",principal.request.getUri().asURI(),session_upstream.getId());
            				session_upstream.setMaxTextMessageBufferSize(session_client.getMaxTextMessageBufferSize());
            				session_upstream.setMaxIdleTimeout(session_client.getMaxIdleTimeout());
            				session_upstream.addMessageHandler(new MessageHandler.Whole<String>() {
            		            public void onMessage(String message) {
            		            	try {
            		            		if (logger.isTraceEnabled()) {
            		            			logger.trace("->{}: {}",principal.request.getUri().asURI(),message);
            		            		}
            		            		authorize();
           		                    	session_client.getBasicRemote().sendText(message);
            		                } catch (Throwable e) {
            		                	final CloseReason reason=closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"upstream message error: "+e.toString());
            		                	try {
            		                    	session_client.close(reason);
            		                    } catch (Throwable e1) {}
            		                	try {
            		                    	session_upstream.close(reason);
            		                    } catch (Throwable e1) {}
            		                    
            		                }
            		            }
            		        });
            		    	
            				session_upstream.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            		            public void onMessage(ByteBuffer message) {
            		            	try {
            		            		if (logger.isTraceEnabled()) {
            		            			logger.trace("->{}: {}",principal.request.getUri().asURI(),message.capacity());
            		            		}
            		            		authorize();
           		                    	session_client.getBasicRemote().sendBinary(message);
            		                } catch (Throwable e) {
            		                	final CloseReason reason=closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"upstream message error: "+e.toString());
            		                	try {
            		                    	session_client.close(reason);
            		                    } catch (Throwable e1) {}
            		                	try {
            		                    	session_upstream.close(reason);
            		                    } catch (Throwable e1) {}
            		                }
            		            }
            		        });
						}
						
            			@Override
						public void onClose(Session session_upstream, CloseReason reason) {
            				logger.debug("upstream close {} {}: {}",principal.request.getUri().asURI(),session_upstream.getId(),reason);
            		    	try {
            		    		session_client.close(reason);
            		        } catch (Throwable ioe) {}
            		    }
					},
            		configBuilder.build(),
            		principal.request.getUri().asURI()
            		);
        } catch (Exception e) {
        	logger.error("{}: {}",principal.request.getUri().asURI(),e.toString());
        	try {
				session_client.close(closeReason(CloseReason.CloseCodes.TRY_AGAIN_LATER,"upstream down: "+e.toString()));
			} catch (IOException e1) {}
        }
    }
    
    @OnClose
    public void end(Session session_client,CloseReason reason) throws IOException {
    	logger.debug("client close {} {}: {}",session_client.getRequestURI(),session_client.getId(),reason);

		closeUpstream(reason);
    }
    
    @OnError
    public void onError(Session session_client,Throwable t) throws Throwable {
    	try {
       		session_client.close();
        } catch (Throwable e1) {}
    	closeUpstream(closeReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"client error: "+t.toString()));
    }
    
    @OnMessage
    public void echoTextMessage(Session session_client,String msg, boolean last) {
    	if (logger.isTraceEnabled()) {
    		logger.trace("->{}: {}",session_client.getRequestURI(),msg);
    	}
        try {
        	final Session upstream=upstream();
        	authorize();
           	upstream.getBasicRemote().sendText(msg, last);
        } catch (Throwable e) {
        	closeAbnormally(session_client, "client message error: "+e.toString());
        }
    }

    @OnMessage
    public void echoBinaryMessage(Session session_client,ByteBuffer bb,boolean last) {
    	if (logger.isTraceEnabled()) {
    		logger.trace("->{}: {}",session_client.getRequestURI(),bb.capacity());
    	}
        try {
        	final Session upstream=upstream();
        	authorize();
            upstream.getBasicRemote().sendBinary(bb, last);
        } catch (Throwable e) {
        	closeAbnormally(session_client, "client message error: "+e.toString());
        }
    }
}
