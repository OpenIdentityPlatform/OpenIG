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
		final Long ttl=Long.parseLong(System.getProperty("org.openidentityplatform.openig.websocket.ttl", "180"))*1000; 
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
