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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Principal implements java.security.Principal {
	private static final Logger logger=LoggerFactory.getLogger(Principal.class);
	
	
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

	/** System property holding the re-authorization interval, in seconds. */
	static final String TTL_PROPERTY="org.openidentityplatform.openig.websocket.ttl";
	static final long DEFAULT_TTL_SECONDS=180;
	private static final long TTL_MILLIS=ttlMillis(System.getProperty(TTL_PROPERTY));

	/** Parses the re-authorization interval, falling back to the default when the value is not a number. */
	static long ttlMillis(String seconds) {
		if (seconds!=null) {
			try {
				return Long.parseLong(seconds.trim())*1000;
			} catch (NumberFormatException e) {
				logger.warn("Ignoring invalid value \"{}\" of system property {}, using {} seconds", seconds, TTL_PROPERTY, DEFAULT_TTL_SECONDS);
			}
		}
		return DEFAULT_TTL_SECONDS*1000;
	}

	public Status authorize() throws InterruptedException, ExecutionException {
		final long ttl=TTL_MILLIS;
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
