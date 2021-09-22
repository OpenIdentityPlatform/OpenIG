package org.openidentityplatform.openig.mq;

import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.util.Options.defaultOptions;

import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class EmbeddedKafkaTest {

	EmbeddedKafka kafka=new EmbeddedKafka();
	
	JsonValue config=json(object(
			            field("zookeper.port", "${system['unknown']}")
			            )
					);
	@Before
	public void start() throws Exception {
		kafka.create(Name.of("test"), config, buildDefaultHeap());
	}
	
	@Test 
	public void test() throws HeapException {
	}
	
	@After
	public void stop() throws HeapException {
		kafka.destroy();
	}
	
	private HeapImpl buildDefaultHeap() throws Exception {
        final HeapImpl heap = new HeapImpl(Name.of("myHeap"));
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, newTemporaryStorage());
        heap.put(CLIENT_HANDLER_HEAP_KEY, new ClientHandler(new HttpClientHandler(defaultOptions())));
        return heap;
    }
}
