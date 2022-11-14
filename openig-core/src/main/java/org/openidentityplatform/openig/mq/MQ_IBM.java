package org.openidentityplatform.openig.mq;

import java.util.Map.Entry;

import javax.jms.*;

import java.util.Collections;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.TimeUnit;

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
import com.ibm.msg.client.jms.JmsConnectionFactory;
import com.ibm.msg.client.jms.JmsFactoryFactory;
import com.ibm.msg.client.wmq.WMQConstants;

public class MQ_IBM implements Handler{
    private static final Logger logger = LoggerFactory.getLogger(MQ_IBM.class);
    
    String name;
    String topic;

    JmsConnectionFactory cf;
    
    @Override
	public Promise<Response, NeverThrowsException> handle(Context context, Request request) {
		try {
			if (topic==null || topic.isEmpty()) {
				logger.warn("{}: please config \"topic.produce\" ", name);
				Response response = new Response(Status.NOT_IMPLEMENTED);
			    return Promises.newResultPromise(response);
			}
			final JMSContext producerContext=cf.createContext(Session.AUTO_ACKNOWLEDGE);
			
			final BytesMessage jmsMessage=producerContext.createBytesMessage();
	        jmsMessage.setJMSCorrelationID(UUID.randomUUID().toString()); 
	        //jmsMessage.setJMSReplyTo(receive);
	        for (Entry<String, Header> entry: request.getHeaders().asMapOfHeaders().entrySet()) {
        		jmsMessage.setStringProperty(entry.getKey().replaceAll("-", "__"), entry.getValue().getFirstValue());
	        }
	        jmsMessage.writeBytes(request.getEntity().getBytes());
	        
	        final JMSProducer producer=producerContext.createProducer();
	        final Destination dest=producerContext.createQueue(topic);
	        producer.send(dest, jmsMessage);
	        producerContext.close();
	        
			Response response = new Response(Status.ACCEPTED);
		    return Promises.newResultPromise(response);
		}catch (Exception e) {
			logger.warn("An error occurred while processing the request: {}", e.toString());
			Response response = new Response(Status.INTERNAL_SERVER_ERROR);
		    return Promises.newResultPromise(response);
		}
	}
	
    public static class Heaplet extends GenericHeaplet {

        private static final Logger logger = LoggerFactory.getLogger(Heaplet.class);

        JsonValue evaluated;
        MQ_IBM handler;
        
        @Override
        public MQ_IBM create() throws HeapException {
            //final Options options = Options.defaultOptions();
            evaluated = config.as(evaluatedWithHeapProperties());
            
            handler=new MQ_IBM();
            return handler;
        }
        
        ExecutorService consumeService = null;
        @Override
		public void start() throws HeapException {
			super.start();
			
			try {
				handler.topic=evaluated.get("topic.produce").asString();
				handler.name=name;
				
				handler.cf=JmsFactoryFactory.getInstance(WMQConstants.WMQ_PROVIDER).createConnectionFactory();
				handler.cf.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT);
				
				handler.cf.setStringProperty(WMQConstants.WMQ_CONNECTION_NAME_LIST, evaluated.get(WMQConstants.WMQ_CONNECTION_NAME_LIST).defaultTo("localhost(1414)").asString() );
				handler.cf.setStringProperty(WMQConstants.WMQ_CHANNEL, evaluated.get(WMQConstants.WMQ_CHANNEL).defaultTo("DEV.APP.SVRCONN").asString());
				handler.cf.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, evaluated.get(WMQConstants.WMQ_QUEUE_MANAGER).defaultTo("QM1").asString());
				handler.cf.setStringProperty(WMQConstants.USERID, evaluated.get(WMQConstants.USERID).defaultTo("app").asString());
				handler.cf.setStringProperty(WMQConstants.PASSWORD, evaluated.get(WMQConstants.PASSWORD).defaultTo("").asString());
				for (Entry<String, Object> entry: evaluated.asMap(Object.class).entrySet()) {
					if (!handler.cf.containsKey(entry.getKey()) && entry.getValue()!=null) {
						handler.cf.setStringProperty(entry.getKey(),entry.getValue().toString());
					}
				} 
				final int core=evaluated.get("core").defaultTo(Runtime.getRuntime().availableProcessors()*8).asInteger();
				if (core>0 && evaluated.get("topic.consume")!=null && evaluated.get("topic.consume").asString()!=null && !evaluated.get("topic.consume").asString().isEmpty()) {
					consumeService=Executors.newFixedThreadPool(core,new ThreadFactoryBuilder().setNameFormat(name+"-consumer-%d").build());
					for(int i=1;i<=core;i++) {
						consumeService.submit(new Runnable() {
							@SuppressWarnings({ "unchecked", "static-access" })
							@Override
							public void run() {
								while (true) {
									try (JMSContext jmsc=handler.cf.createContext(Session.AUTO_ACKNOWLEDGE)){
										try (JMSConsumer consumer=jmsc.createConsumer(jmsc.createQueue(evaluated.get("topic.consume").asString()))){
											while (true){
												final Message message=consumer.receive();
												if (logger.isTraceEnabled() ) {
									        		 logger.trace("consume {}",message);
									            }
									        	try {
									        		final Request request=new Request();
									        		request.setMethod(evaluated.get("method").defaultTo("PUT").asString());
													Object entity = (message instanceof TextMessage) ? ((TextMessage)message).getText() : message.getBody(byte[].class);
													request.setEntity(entity);
										        	request.setUri(evaluated.get("uri").defaultTo("/"+name).asString());
										        	request.getHeaders().add("JMSCorrelationID", message.getJMSCorrelationID());
										        	 
										        	for (String header : Collections.list((Enumeration<String>)message.getPropertyNames())) {
										        		request.getHeaders().add(header.replace("__", "-"), message.getStringProperty(header));
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
									}catch (Exception e) {
										logger.error("{}",e);
										try {
											Thread.currentThread().sleep(60*1000);
										} catch (InterruptedException e1) {
											return;
										}
									}
								}
							}
						});
					}
				}else {
					logger.warn("{} ignore \"topic.consume\"={} \"core\"={}",name,evaluated.get("topic.consume").asString(),core);
				}
			}catch (Exception e) {
				throw new HeapException(e);
			}
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
            handler.cf=null;
        }
    }
}
