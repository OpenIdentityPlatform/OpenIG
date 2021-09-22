package org.openidentityplatform.openig.mq;


import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.kafka.common.utils.Time;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;

public class EmbeddedKafka extends GenericHeaplet{
	private static final Logger logger = LoggerFactory.getLogger(EmbeddedKafka.class);
	 
	private JsonValue evaluated;
	
	@Override
	public Object create() throws HeapException {
		evaluated = config.as(evaluatedWithHeapProperties());
		return this;
	}

	NIOServerCnxnFactory connectionFactory;
	ZooKeeperServer zk;
	KafkaServer server;
    @Override
	public void start() throws HeapException {
		super.start();
		try {
			
			final Path zookeeperBaseDir=evaluated.isDefined("path")?Paths.get(evaluated.get("path").required().asString()):Files.createTempDirectory("kafka-embedded").toAbsolutePath();
			
			final String zookeeperPort=evaluated.get("zookeper.port").asString();
			if ((zookeeperPort==null || zookeeperPort.isEmpty()) && !evaluated.isDefined("zookeper.connect")) {
				logger.warn("{} ignored: define \"zookeper.port\" for emedded zookeper or \"zookeper.connect\" for use remote zookeper",this.name);
				return;
			}
			
			if (zookeeperPort!=null && !zookeeperPort.isEmpty()) {
				zk=new ZooKeeperServer(zookeeperBaseDir.resolve("zk.log").toFile(),zookeeperBaseDir.resolve("zk.data").toFile(),1000);
		        
		        connectionFactory= new NIOServerCnxnFactory();
		        connectionFactory.configure(new InetSocketAddress(Integer.parseInt(zookeeperPort)),0);
		        connectionFactory.startup(zk);
			}
			
			final Properties properties = new Properties();
			properties.setProperty("zookeeper.connect", (zookeeperPort!=null)?evaluated.get("zookeper.connect").defaultTo("localhost:"+zookeeperPort).asString():evaluated.get("zookeper.connect").required().asString());
			properties.setProperty("offsets.topic.replication.factor", evaluated.get("offsets.topic.replication.factor").defaultTo("1").asString());
			properties.setProperty("log.dir",evaluated.get("log.dir").defaultTo(zookeeperBaseDir.resolve("log.dir").toAbsolutePath().toString()).asString());
			for (Entry<String, Object> entry: evaluated.asMap(Object.class).entrySet()) {
				if (!properties.containsKey(entry.getKey()) && entry.getValue()!=null) {
					properties.setProperty(entry.getKey(),entry.getValue().toString());
				}
			} 
			server=new KafkaServer(new KafkaConfig(properties), Time.SYSTEM, scala.Option.apply(null), false);
			server.startup();
		}catch (Exception e) {
			throw new HeapException(e);
		}
	}

	@Override
    public void destroy() {
		super.destroy();
		if (server!=null) {
			server.shutdown();
			server.awaitShutdown();
		}
		server=null;
		
		if (zk!=null) {
			zk.shutdown();
		}
		zk=null;
		
		if (connectionFactory!=null) {
			connectionFactory.shutdown();
	        try {
				connectionFactory.join();
			} catch (InterruptedException e) {}
		}
		connectionFactory=null;
    }
}
