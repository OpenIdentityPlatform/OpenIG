package org.openidentityplatform.openig.mq;

import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.util.Options.defaultOptions;

import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.RootContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openidentityplatform.openig.mq.MQ_IBM.Heaplet;


public class MQ_IBMTest {

	
	Heaplet heaplet=new MQ_IBM.Heaplet();
	MQ_IBM handler;
	
	JsonValue configMQConsumer=json(object(
            field("topic.produce", "DEV.QUEUE.1"),
            field("topic.consume", "DEV.QUEUE.1"),
            field("uri", "${'/test-uri'}"),
            field("XMSC_PASSWORD","passw0rd")
            )
		);
	
	// docker run --env LICENSE=accept --env MQ_QMGR_NAME=QM1 --publish 1414:1414 --publish 9443:9443  --env MQ_APP_PASSWORD=passw0rd ibmcom/mq:latest
	@Before
	public void start() throws Exception {
		handler=(MQ_IBM)heaplet.create(Name.of("test"), configMQConsumer, buildDefaultHeap());
	}
	
	@Test 
	public void test() throws HeapException, URISyntaxException, InterruptedException {
	 	ExecutorService pool=Executors.newFixedThreadPool(16);
   	 	for(int i=0;i<10000;i++) {
   	 		pool.submit(new Runnable() {
   	 			@Override
				public void run() {
	   	 			final Request request=new Request();
	   	 		
	   	 			request.setMethod("PUT");
	   	    	 	request.setEntity("test body");
	   	    	 	try {
						request.setUri("/uri");
					} catch (URISyntaxException e) {}
	   	    	 	request.getHeaders().add("header", "header");
   	    	 	
					handler.handle(new RootContext(), request);
				}
			});
	 	}
   	 	pool.shutdown();
   	 	pool.awaitTermination(60, TimeUnit.MINUTES);
	}
	
	@After
	public void stop() throws HeapException {
		heaplet.destroy();
	}
	
	private HeapImpl buildDefaultHeap() throws Exception {
        final HeapImpl heap = new HeapImpl(Name.of("myHeap"));
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, newTemporaryStorage());
        heap.put(CLIENT_HANDLER_HEAP_KEY, new ClientHandler(new HttpClientHandler(defaultOptions())));
        return heap;
    }
}
