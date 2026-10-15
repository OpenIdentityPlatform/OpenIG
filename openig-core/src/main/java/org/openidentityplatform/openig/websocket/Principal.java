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
 * Copyright 2022-2026 3A Systems, LLC.
 */

package org.openidentityplatform.openig.websocket;

import java.util.concurrent.ExecutionException;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.servlet.HttpFrameworkServlet;
import org.forgerock.services.context.Context;

public class Principal implements java.security.Principal {
	
	
	final Context context; 
	final Request request;
	
	public Principal(final Context context,final Request request) {
		this.context=context;
		this.request=request;
	}
	
	@Override
	public String getName() {
		return null;
	}
	
	long lastAuthorize=0;
	Status res=null;

	public Status authorize() throws InterruptedException, ExecutionException {
		final long ttl=Long.parseLong(System.getProperty("org.openidentityplatform.openig.websocket.ttl", "180"))*1000;
		if (res==null || System.currentTimeMillis()-lastAuthorize>=ttl) {
			synchronized (this) {
				if (res==null || (System.currentTimeMillis()-lastAuthorize)>=ttl) {
					res=HttpFrameworkServlet.getGatewayHttpFrameworkServlet().handler.handle(context, request).get().getStatus();
					lastAuthorize=System.currentTimeMillis();
				}
			}
		}
		return res;
	}
}
