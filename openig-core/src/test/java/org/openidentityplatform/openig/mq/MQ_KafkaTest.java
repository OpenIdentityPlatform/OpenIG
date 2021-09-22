package org.openidentityplatform.openig.mq;

import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.util.Options.defaultOptions;

import java.net.URISyntaxException;

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


public class MQ_KafkaTest {

	EmbeddedKafka server=new EmbeddedKafka();
	
	MQ_Kafka.Heaplet heaplet=new MQ_Kafka.Heaplet();
	MQ_Kafka handler;
	
	JsonValue configKafkaEmbedded=json(object(
						field("zookeper.port", "${'2181'}")
			            )
					);
	
	JsonValue configMQConsumer=json(object(
            field("topic.consume", "${'testTopic'}"),
            field("topic.produce", "${'testTopic'}"),
            field("uri", "${'/test-uri'}")
            )
		);
	
	@Before
	public void start() throws Exception {
		server.create(Name.of("test"), configKafkaEmbedded, buildDefaultHeap());
		handler=(MQ_Kafka)heaplet.create(Name.of("test"), configMQConsumer, buildDefaultHeap());
	}
	
	@Test 
	public void test() throws HeapException, URISyntaxException {
		final Request request=new Request();
		
		request.setMethod("PUT");
   	 	request.setEntity("test body");
   	 	request.setUri("/uri");
   	 	request.getHeaders().add("header", "header");
   	 	
   	 	for(int i=0;i<10000;i++) {
   	 		handler.handle(new RootContext(), request);
   	 	}
	}
	
	@After
	public void stop() throws HeapException {
		server.destroy();
		heaplet.destroy();
	}
	
	private HeapImpl buildDefaultHeap() throws Exception {
        final HeapImpl heap = new HeapImpl(Name.of("myHeap"));
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, newTemporaryStorage());
        heap.put(CLIENT_HANDLER_HEAP_KEY, new ClientHandler(new HttpClientHandler(defaultOptions())));
        return heap;
    }
}
