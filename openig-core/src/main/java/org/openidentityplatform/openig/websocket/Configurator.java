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

import java.util.List;

import jakarta.websocket.Extension;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

public class Configurator extends ServerEndpointConfig.Configurator {

	@Override
	public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
		return super.getNegotiatedSubprotocol(requested, requested);
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
