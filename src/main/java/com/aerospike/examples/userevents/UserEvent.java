package com.aerospike.examples.userevents;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Info;
import com.aerospike.client.Key;
import com.aerospike.client.Language;
import com.aerospike.client.Record;
import com.aerospike.client.Value;
import com.aerospike.client.cluster.Node;
import com.aerospike.client.policy.InfoPolicy;
import com.aerospike.client.task.RegisterTask;


/**
@author Peter Milne
*/
public class UserEvent {
	public static final String EVENTS_MAP_SET = "events-map";
	public static final int MAX_EVENTS_PER_USER = 500;
	public static final int USER_TOTAL = 1000;
	public static final String EVENTS_RECORDS_SET = "events-records";
	public static final long MILLI_SEC_IN_DAY = 1000 * 60 * 60 * 24; // not super accurate, but close
	private AerospikeClient client;
	private String seedHost;
	private int port;
	private String namespace;
	private String[] campaighList = new String[]{"cats", "dogs","mice","sheep","chickens","cows","snakes","fish","elephants","birds","lizards"};
	private String[] actionList = new String[]{"click","tap","clap","dance", "climb","walk","run","swim","sail","fly","drive"};

	private static Logger log = Logger.getLogger(UserEvent.class);
	public UserEvent(String host, int port, String namespace) throws AerospikeException {
		this.client = new AerospikeClient(host, port);
		this.seedHost = host;
		this.port = port;
		this.namespace = namespace;
	}
	public UserEvent(AerospikeClient client, String namespace) throws AerospikeException {
		this.client = client;
		this.namespace = namespace;
	}
	public static void main(String[] args) throws AerospikeException {
		try {
			Options options = new Options();
			options.addOption("h", "host", true, "Server hostname (default: 127.0.0.1)");
			options.addOption("p", "port", true, "Server port (default: 3000)");
			options.addOption("n", "namespace", true, "Namespace (default: test)");
			options.addOption("l", "load", false, "Load data");
			options.addOption("u", "usage", false, "Print usage.");

			CommandLineParser parser = new PosixParser();
			CommandLine cl = parser.parse(options, args, false);


			String host = cl.getOptionValue("h", "127.0.0.1");
			String portString = cl.getOptionValue("p", "3000");
			int port = Integer.parseInt(portString);
			String namespace = cl.getOptionValue("n", "test");
			String set = cl.getOptionValue("s", "");
			log.debug("Host: " + host);
			log.debug("Port: " + port);
			log.debug("Namespace: " + namespace);
			log.debug("Set: " + set);

			@SuppressWarnings("unchecked")
			List<String> cmds = cl.getArgList();
			if (cmds.size() == 0 && cl.hasOption("u")) {
				logUsage(options);
				return;
			}

			UserEvent as = new UserEvent(host, port, namespace);
			
			as.registerUDF();
			
			if (cl.hasOption("l")){
				as.load();
			} else {
				as.work();
			}

		} catch (Exception e) {
			log.error("Critical error", e);
		}
	}
	/**
	 * Write usage to console.
	 */
	private static void logUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		String syntax = UserEvent.class.getName() + " [<options>]";
		formatter.printHelp(pw, 100, syntax, "options:", options, 0, 2, null);
		log.info(sw.toString());
	}

	public void work() throws Exception {
		/*
		 * How many times did a user u done action a on campaign c in the past 30/60/90 days?
		 */
		String user = "user689";
		String action = "tap";
		String campaign = "birds";
		long days = 90;
		long now = System.currentTimeMillis();
		long timeDelta = now - (days * MILLI_SEC_IN_DAY);
		/*
		 * data in a map
		 */
		log.info("How many times did a user "+user+" did action "+action+" on campaign "+campaign+" in the past "+days+" days?");
		log.info("*** data as map ***");
		long start = System.currentTimeMillis();
		Key key = new Key(this.namespace, EVENTS_MAP_SET, user);
		Record userRecord = this.client.get(null, key, "events");
		int count = 0;
		if (userRecord != null){
			Map<Long, String> userEvents = (Map<Long, String>) userRecord.getValue("events");
			if (userEvents != null){
				for (Entry<Long, String> event :userEvents.entrySet()){
					String[] eventParts = event.getValue().split(":");

					if (event.getKey() > timeDelta &&
							eventParts[0].equals(campaign) &&
							eventParts[1].equals(action)){
						count++;
					}
				}
			}
		}
		long stop = System.currentTimeMillis();
		log.info(String.format("%d, in %dms", count, (stop-start)));
		/*
		 * data in a map using UDF
		 */
		log.info("*** data as map using UDF***");
		start = System.currentTimeMillis();
		
		long countL = (Long) this.client.execute(null, key, "event_module", "count", Value.get(action), Value.get(campaign), Value.get(timeDelta));
		
		stop = System.currentTimeMillis();
		log.info(String.format("%d, in %dms", countL, (stop-start)));
		/*
		 * data as individual records
		 */
		log.info("*** data as records ***");
		count = 0;
		start = System.currentTimeMillis();
		key = new Key(this.namespace, EVENTS_RECORDS_SET, user);
		userRecord = this.client.get(null, key, "event-count");
		if (userRecord != null){
			int countRecord = userRecord.getInt("event-count");
			if (countRecord > 0){
				Key[] keyArray = new Key[countRecord];
				for (int i = 0; i < countRecord; i++){
					keyArray[i] = new Key(this.namespace, EVENTS_RECORDS_SET, user+":"+(countRecord-i));
				}

				Record[] eventRecords = this.client.get(null, keyArray);

				for (Record eventRecord : eventRecords){
					if (eventRecord == null)
						continue;
					long ts = eventRecord.getLong("ts");
					if (ts < timeDelta)
						continue;
					String event = eventRecord.getString("event");
					String[] eventParts = event.split(":");
					if (eventParts[0].equals(campaign) &&
							eventParts[1].equals(action)){
						count++;
					}
				}
			}
		}
		stop = System.currentTimeMillis();
		log.info(String.format("%d, in %dms", count, (stop-start)));

	}
	
	public void load() throws InterruptedException {
		//set TTL to 90 days
		this.client.writePolicyDefault.expiration = 90 * 24 * 60 * 60;
		
		Random rand = new Random(10);
		long start = System.currentTimeMillis();
		/*
		 * create user and event data in EVENTS_MAP_SET
		 */
		log.info("Writting events");
		for (int i = 0; i < USER_TOTAL; i++){
			String userID = "user" + i;
			Key key = new Key(this.namespace, EVENTS_MAP_SET, userID);
			Key keyRecord = new Key(this.namespace, EVENTS_RECORDS_SET, userID);
			Bin name = new Bin("name", userID);
			this.client.put(null, keyRecord, name);
			// where key is timestamp, value is <campaign_id>:<action>
			Map<Long, String> events = new HashMap<Long, String>();
			int noOfEvents = rand.nextInt(MAX_EVENTS_PER_USER);
			for (int y = 0; y < noOfEvents; y++){
				Thread.sleep(1);
				
				// Make a map
				int c = rand.nextInt(this.campaighList.length-1);
				int a = rand.nextInt(this.actionList.length-1);
				String eventString = this.campaighList[c] + ":" + this.actionList[a];
				long ts = System.currentTimeMillis();
				events.put(ts, eventString);
				
				// Make individual records
				Key eventKey = new Key(this.namespace, EVENTS_RECORDS_SET, userID + ":" + (y+1));
				Bin eventBin = new Bin("event", eventString);
				Bin tsBin = new Bin("ts", ts);
				this.client.put(null, eventKey, name, tsBin, eventBin);
				this.client.add(null, keyRecord, new Bin("event-count", 1));

			}
			Bin eventsBin = new Bin ("events", events);
			this.client.put(null, key, name, eventsBin);
		}
		
		long stop = System.currentTimeMillis();
		log.info(String.format("Completed data load in: %d ms", stop - start));

	}
	
	public void registerUDF() {
		/*
		 * check and register UDF
		 */
		Node[] nodes = client.getNodes();
		String infoResult = Info.request(new InfoPolicy(), nodes[0], "udf-list");
		if (!infoResult.contains("event_module.lua")){
			RegisterTask rt = client.register(null, "udf/event_module.lua", "event_module.lua", Language.LUA);
			rt.waitTillComplete();
		}
	}

}