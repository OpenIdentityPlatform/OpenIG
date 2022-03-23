package org.openidentityplatform.openig.filter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map.Entry;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.rfc3507.client.ICAPClient;
import net.rfc3507.client.ICAPException;
import net.rfc3507.client.ICAPRequest;
import net.rfc3507.client.ICAPRequest.Mode;
import net.rfc3507.client.ICAPResponse;

public class ICAPFilter implements Filter {

	private static final Logger logger = LoggerFactory.getLogger(ICAPFilter.class);
	
	ICAPClient icap=null;
	String service=null;
	Boolean rewrite=true;
	
	public static class Heaplet extends GenericHeaplet {
		
		ICAPFilter filter;
		
		@Override
        public Object create() throws HeapException {
			filter=new ICAPFilter();
            return filter;
        }

		@Override
		public void start() throws HeapException {
			super.start();
			String server=config.get("server").required().as(expression(String.class)).eval();
			
			if (server==null || server.trim().isEmpty()) {
				logger.debug("server is empty");
				return;
			}
			try {
				final URI uri=new URI(server);
				filter.icap=new ICAPClient(uri.getHost(), uri.getPort()>0?uri.getPort():1344);
				filter.icap.setConnectTimeout(Integer.parseInt(config.get("connect_timeout").defaultTo("5000").as(expression(String.class)).eval()));
				filter.icap.setReadTimeout(Integer.parseInt(config.get("read_timeout").defaultTo("15000").as(expression(String.class)).eval()));
				filter.service=config.get("service").defaultTo("").as(expression(String.class)).eval();
				filter.rewrite=Boolean.parseBoolean(config.get("rewrite").defaultTo("true").as(expression(String.class)).eval());
				logger.info("start {} connect_timeout={} read_timeout={}",uri,filter.icap.getConnectTimeout(),filter.icap.getReadTimeout());
			} catch (URISyntaxException e) {
				logger.warn("invalid server format: \"{}\" use icap://server:port",server);
			}
		}
		
		@Override
		public void destroy() {
			super.destroy();
			if (filter.icap!=null) {
				filter.icap=null;
			}
		}
    }

	@Override
	public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
		if (icap!=null && !request.getEntity().isRawContentEmpty()) {
			final ICAPRequest req=new ICAPRequest(service, Mode.REQMOD);
			try {
				req.setHttpRequestBody(request.getEntity().getBytes());
				final ICAPResponse res=icap.execute(req);
				if (res.getStatus()==204) { //allow content
					return next.handle(context, request);
				}else if (res.getStatus()==200) { //modify response 
					request.getHeaders().add("x-icap-status", ""+res.getStatus());
					request.getHeaders().add("x-icap-message", res.getMessage());
					for (Entry<String, List<String>> header : res.getHeaderEntries().entrySet()) {
						request.getHeaders().add(header.getKey(), header.getValue());
					}
					if (rewrite) {
						final ICAPResponse.ResponseHeader responseHeader=res.getResponseHeader();
						final Response response = new Response(Status.valueOf(responseHeader.getStatus(), responseHeader.getMessage()));
						for (Entry<String, List<String>> header : responseHeader.getHeaderEntries().entrySet()) {
							response.getHeaders().add(header.getKey(), header.getValue());
						}
						response.setEntity(res.getHttpShrinkResponseBody());
					    return Promises.newResultPromise(response);
					}
				}else { //ignore error
					logger.error("{}",res);
				}
			}catch (ICAPException e) {
				logger.error("{}",e.toString());
			}catch (IOException e) {
				logger.warn("{}",e.toString());
			}
		}
		return next.handle(context, request);
	}

}
