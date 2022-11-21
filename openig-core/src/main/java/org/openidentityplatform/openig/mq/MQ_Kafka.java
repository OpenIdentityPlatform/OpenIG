package org.openidentityplatform.openig.mq;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Header;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.servlet.HttpFrameworkServlet;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.promise.RuntimeExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class MQ_Kafka implements Handler{
    private static final Logger logger = LoggerFactory.getLogger(MQ_Kafka.class);

    Producer<String, byte[]> producer;
    
    String name;
    String topic;
    
	@Override
	public Promise<Response, NeverThrowsException> handle(Context context, Request request) {
		try {
			if (producer==null) {
				logger.warn("{}: please config \"topic.produce\" ", name);
				Response response = new Response(Status.NOT_IMPLEMENTED);
			    return Promises.newResultPromise(response);
			}
			final List<org.apache.kafka.common.header.Header> headers=new ArrayList<org.apache.kafka.common.header.Header>();
			for (Entry<String, Header> entry: request.getHeaders().asMapOfHeaders().entrySet()) {
				headers.add(new org.apache.kafka.common.header.Header() {
					@Override
					public byte[] value() {
						try {
							return entry.getValue().getFirstValue().getBytes("UTF-8");
						} catch (UnsupportedEncodingException e) {
							throw new RuntimeException(e);
						}
					}
					
					@Override
					public String key() {
						return entry.getKey();
					}
				});
			}
			final String key=request.getHeaders().getFirst("correlation-id")==null?UUID.randomUUID().toString():request.getHeaders().getFirst("correlation-id");
			final RecordMetadata record=producer.send(new ProducerRecord<String, byte[]>( topic, null,key,request.getEntity().getBytes(),headers)).get();
			final Response response = new Response(Status.ACCEPTED);
			response.getHeaders().add("correlation-id", key);
			response.getHeaders().add("kafka-topic", record.topic());
			response.getHeaders().add("kafka-key", key);
			response.getHeaders().add("kafka-offset", ""+record.offset());
			response.getHeaders().add("kafka-timestamp", ""+record.timestamp());
			response.getHeaders().add("kafka-timestamp-date", ""+new Date(record.timestamp()));
		    return Promises.newResultPromise(response);
		}catch (Exception e) {
			logger.warn("An error occurred while processing the request: {}", e.toString());
			final Response response = new Response(Status.INTERNAL_SERVER_ERROR);
		    return Promises.newResultPromise(response);
		}
		
		
	}
	
	   /** Creates and initializes a client handler in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        private static final Logger logger = LoggerFactory.getLogger(Heaplet.class);

        JsonValue evaluated;
        MQ_Kafka handler;
        
        @Override
        public MQ_Kafka create() throws HeapException {
            evaluated = config.as(evaluatedWithHeapProperties());

            handler=new MQ_Kafka();
            
            return handler;
        }

        ExecutorService consumeService = null;
        @Override
		public void start() throws HeapException {
			super.start();
			if (evaluated.get("topic.consume")!=null && evaluated.get("topic.consume").asString()!=null && !evaluated.get("topic.consume").asString().isEmpty()) {
				final int core=evaluated.get("core").defaultTo(Runtime.getRuntime().availableProcessors()*64).asInteger();
				
				consumeService=Executors.newFixedThreadPool(core,new ThreadFactoryBuilder().setNameFormat(name+"-consumer-%d").build());
				for(int i=1;i<=core;i++) {
					consumeService.submit(  
					    new Runnable() {
							@Override
							public void run() {
								logger.info("start consumer");
								final Properties propsConsumer = new Properties();
								propsConsumer.setProperty("bootstrap.servers", evaluated.get("bootstrap.servers").defaultTo("localhost:9092").asString());
								propsConsumer.setProperty("group.id", evaluated.get("group.id").defaultTo(Thread.currentThread().getName()).asString());
								propsConsumer.setProperty("enable.auto.commit", evaluated.get("enable.auto.commit").defaultTo("true").asString());
								propsConsumer.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
								propsConsumer.setProperty("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
								for (Entry<String, Object> entry: evaluated.asMap(Object.class).entrySet()) {
									if (!propsConsumer.containsKey(entry.getKey()) && entry.getValue()!=null) {
										propsConsumer.setProperty(entry.getKey(),entry.getValue().toString());
									}
								} 
								try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(propsConsumer)){
								    consumer.subscribe(Arrays.asList(evaluated.get("topic.consume").required().asString()));
									while (true) {
								         for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(Long.MAX_VALUE))) {
								        	 if (logger.isTraceEnabled() ) {
								        		 logger.trace("consume {}",record);
								             }
								        	 try {
									        	 final Request request=new Request();
									        	 request.setMethod(evaluated.get("method").defaultTo("PUT").asString());
									        	 request.setEntity(record.value());
									        	 request.setUri(evaluated.get("uri").defaultTo("/"+name).asString());
									        	 request.getHeaders().add("kafka-topic", record.topic());
									        	 request.getHeaders().add("correlation-id", record.key()==null?UUID.randomUUID().toString():record.key());
									        	 request.getHeaders().add("kafka-key", record.key());
									        	 request.getHeaders().add("kafka-offset", ""+record.offset());
									        	 request.getHeaders().add("kafka-timestamp", ""+record.timestamp());
									        	 request.getHeaders().add("kafka-timestamp-date", ""+new Date(record.timestamp()));
									        	 for (org.apache.kafka.common.header.Header header : record.headers()) {
									        		 request.getHeaders().add(header.key(), new String(header.value(),"UTF-8"));
									        	 } 
									        	 
									        	 if (HttpFrameworkServlet.getRootHandler()!=null) {
										        	 HttpFrameworkServlet.getRootHandler().handle(new AttributesContext(new RootContext()), request)
							                           .thenOnResult(new ResultHandler<Response>() {
							                               @Override
							                               public void handleResult(Response response) {
							                               	logger.trace("process done");
							                               }
							                           })
							                           .thenOnRuntimeException(new RuntimeExceptionHandler() {
							                               @Override
							                               public void handleRuntimeException(RuntimeException e) {
							                                   logger.error("RuntimeException caught", e);
							                               }
							                           });
							            		 }else {
							            			 logger.info("process done: {}",request.getEntity().toString());
							            		 }
								        	 }catch (Exception e) {
								        		 logger.error("error process message {}",e);
											}
								         }
								     }
								}
							}
					    }
					);
				}
			}else {
				logger.warn("{} ignore \"topic.consume\"",name);
			}
		    
			final Properties propsProducer = new Properties();
			propsProducer.put("bootstrap.servers", evaluated.get("bootstrap.servers").defaultTo("localhost:9092").asString());
			propsProducer.put("key.serializer","org.apache.kafka.common.serialization.StringSerializer");
			propsProducer.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
			for (Entry<String, Object> entry: evaluated.asMap(Object.class).entrySet()) {
				if (!propsProducer.containsKey(entry.getKey()) && entry.getValue()!=null) {
					propsProducer.setProperty(entry.getKey(),entry.getValue().toString());
				}
			} 
			
			handler.topic=evaluated.get("topic.produce").asString();
			if (handler.topic!=null && !handler.topic.isEmpty()) {
				handler.producer = new KafkaProducer<>(propsProducer);
			}
			handler.name=name;
 		}
        
        @Override
        public void destroy() {
            super.destroy();
            
            if (consumeService!=null) {
            	consumeService.shutdown();
            	try {
					consumeService.awaitTermination(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {}
            	consumeService.shutdownNow();
            	consumeService=null;
            }
            
            if (handler.producer!=null) {
            	handler.producer.close();
            }
            handler.producer=null;
        }

    }
}
