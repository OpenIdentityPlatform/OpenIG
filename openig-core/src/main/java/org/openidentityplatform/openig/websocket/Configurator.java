package org.openidentityplatform.openig.websocket;

import java.util.List;

import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

public class Configurator extends ServerEndpointConfig.Configurator {

	@Override
	public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
		return super.getNegotiatedSubprotocol(supported, requested);
	}

	@Override
	public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
		return super.getNegotiatedExtensions(installed, requested);
	}

	@Override
	public boolean checkOrigin(String originHeaderValue) {
		return super.checkOrigin(originHeaderValue);
	}

	@Override
	public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
		return super.getEndpointInstance(endpointClass);
	}

	@Override
	public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
		super.modifyHandshake(sec, request, response);
	}

}
