package org.openidentityplatform.openig.websocket;

import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/{level1}/{level2}/{level3}",configurator = Configurator.class)
public class ServerEndPoint3 extends ServerEndPoint {
	
}
