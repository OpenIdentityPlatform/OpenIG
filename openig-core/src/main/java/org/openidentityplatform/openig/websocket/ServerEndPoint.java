package org.openidentityplatform.openig.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.ClientEndpointConfig.Builder;
import javax.websocket.server.ServerEndpoint;

import org.forgerock.http.header.GenericHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
						if (entry.getKey().toLowerCase().startsWith("cookie")||entry.getKey().toLowerCase().startsWith("x-")||entry.getKey().equalsIgnoreCase("origin")||entry.getKey().equalsIgnoreCase("Authorization")||entry.getKey().toLowerCase().startsWith("sec-websocket-")) {
							headers.put(entry.getKey(), ((GenericHeader)entry.getValue()).getValues());
						}
					}
        	    }
        	});
           
        	this.session_upstream=ContainerProvider.getWebSocketContainer().connectToServer(
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
