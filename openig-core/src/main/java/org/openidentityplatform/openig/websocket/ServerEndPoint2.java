package org.openidentityplatform.openig.websocket;

import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/{level1}/{level2}",configurator = Configurator.class)
public class ServerEndPoint2 extends ServerEndPoint {
	
}
