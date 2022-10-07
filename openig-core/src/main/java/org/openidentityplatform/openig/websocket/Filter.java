package org.openidentityplatform.openig.websocket;


import java.io.IOException;
import java.security.Principal;
import java.util.Enumeration;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.servlet.HttpFrameworkServlet;
import org.forgerock.http.servlet.ServletSession;
import org.forgerock.http.session.SessionContext;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RequestAuditContext;
import org.forgerock.services.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebFilter(urlPatterns = "/*")
public class Filter implements javax.servlet.Filter {
	private static final Logger logger = LoggerFactory.getLogger(Filter.class);
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {}

	@Override
	public void destroy() {}

	
	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
		final Boolean upgrade="websocket".equalsIgnoreCase(((HttpServletRequest)req).getHeader("Upgrade"));
		if (!upgrade) {
			chain.doFilter(req, resp);
			return;
		}else {
			try {
	        	final HttpFrameworkServlet gateway=HttpFrameworkServlet.getGatewayHttpFrameworkServlet(); 
	        	final Request request = gateway.createRequest((HttpServletRequest)req);
	        	
	        	final AttributesContext attributesContext = new AttributesContext(new RequestAuditContext(gateway.createRouterContext(new SessionContext(new RootContext(), new ServletSession((HttpServletRequest)req)), (HttpServletRequest)req, request)));
	        	final Enumeration<String> attributeNames = req.getAttributeNames();
		        while (attributeNames.hasMoreElements()) {
		            String attributeName = attributeNames.nextElement();
		            attributesContext.getAttributes().put(attributeName, req.getAttribute(attributeName));
		        }

		        final Context context = HttpFrameworkServlet.createClientContext(attributesContext, (HttpServletRequest)req);
		        final org.openidentityplatform.openig.websocket.Principal principal=new org.openidentityplatform.openig.websocket.Principal(context,request);
		        final Status status=principal.authorize();
		        if (status.getCode()==101) {
		        	chain.doFilter(
						new HttpServletRequestWrapper((HttpServletRequest)req) {
							@Override
							public Principal getUserPrincipal() {
								return principal;
							}
						}, 
						resp);
					return;
		        }
		        ((HttpServletResponse)resp).sendError(status.getCode(),status.getReasonPhrase());
	        } catch (Exception e) {
	        	logger.error("error websocket: {}",e.toString(),e);
	        	((HttpServletResponse)resp).sendError(500,"websocket route error");
	            return;
	        }
		}
	}
}
