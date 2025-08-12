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
 * Copyright 2022-2025 3A Systems LLC.
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


@ServerEndpoint(value = "/{level1}",configurator = Configurator.class)
public class ServerEndPoint {
	static Logger logger=LoggerFactory.getLogger(ServerEndPoint.class); 
	
	Principal principal=null;
	Session session_upstream=null;
	
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
            		                	try {
            		                    	session_client.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"upstream message error: "+e.toString()));
            		                    } catch (Throwable e1) {}
            		                	try {
            		                    	session_upstream.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"upstream message error: "+e.toString()));
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
            		                	try {
            		                    	session_client.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"upstream message error: "+e.toString()));
            		                    } catch (Throwable e1) {}
            		                	try {
            		                    	session_upstream.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"upstream message error: "+e.toString()));
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
				session_client.close(new CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER,"upstream down: "+e.toString()));
			} catch (IOException e1) {}
        }
    }
    
    @OnClose
    public void end(Session session_client,CloseReason reason) throws IOException {
    	logger.debug("client close {} {}: {}",session_client.getRequestURI(),session_client.getId(),reason);

		try {
			session_upstream.close(reason);
        } catch (Throwable ioe) {}
	    finally {
			session_upstream=null;
		}
    }
    
    @OnError
    public void onError(Session session_client,Throwable t) throws Throwable {
    	try {
       		session_client.close();
        } catch (Throwable e1) {}
    	try {
       		session_upstream.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"client error: "+t.toString()));
        } catch (Throwable e1) {}
    	finally {
    		session_upstream=null;
		}
    }
    
    @OnMessage
    public void echoTextMessage(Session session_client,String msg, boolean last) {
    	if (logger.isTraceEnabled()) {
    		logger.trace("->{}: {}",session_client.getRequestURI(),msg);
    	}
        try {
        	int ct=0;
        	while ((ct++<5000)&&(session_upstream==null || !session_upstream.isOpen())) {
        		Thread.sleep(1);
        	}
        	authorize();
           	session_upstream.getBasicRemote().sendText(msg, last);
        } catch (Throwable e) {
        	try {
           		session_client.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"client message error: "+e.toString()));
            } catch (Throwable e1) {}
        	try {
           		session_upstream.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"client message error: "+e.toString()));
            } catch (Throwable e1) {}
        	finally {
        		session_upstream=null;
			}
        }
    }

    @OnMessage
    public void echoBinaryMessage(Session session_client,ByteBuffer bb,boolean last) {
    	if (logger.isTraceEnabled()) {
    		logger.trace("->{}: {}",session_client.getRequestURI(),bb.capacity());
    	}
        try {
        	int ct=0;
        	while ((ct++<5000)&&(session_upstream==null || !session_upstream.isOpen())) {
        		Thread.sleep(1);
        	}
        	authorize();
            session_upstream.getBasicRemote().sendBinary(bb, last);
        } catch (Throwable e) {
        	try {
           		session_client.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"client message error: "+e.toString()));
            } catch (Throwable e1) {}
        	try {
           		session_upstream.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,"client message error: "+e.toString()));
            } catch (Throwable e1) {}
        	finally {
        		session_upstream=null;
			}
        }
    }
}
