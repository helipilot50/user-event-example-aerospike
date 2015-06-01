# User Event Example

## Problem
To personalize the user experience when a user access your application, you store events related to that specific user. These may be:
 - click stream
 - purchase history
 - activitiy in an advertising campaign
 - Ads viewed
 - places visited
 - website's visited
 - browser history

This information has value and you may be interested in it for a period of time, or for ever. In this example, the time period is 90 days. Each event contains a time stamp, an action and a campaign or category.

In real time, for a given time period, you need to calculate number of times a specific user has a specific action, for a specific campaign or category.

What is the best way to model data for this in Aerospike?

##Solution
The event data consists of:
 - Timestamp - standard Unix timestamp
 - Action - the action taken by the user
 - Campaign or category - the campaign or category related to the action and timestamp
  
The data is a master detail relation ship where the user has the cardinality of 1 and the related events have the cardinality of 0 or more. 


There are a few ways to model the event data in Aerospike, here are 2:

 - *Option 1*: Events stored as a Map stored in a Bin
 - *Option 2*: Events stored as separate records using a composite key.
 

### How to build

The source code for this solution is available on GitHub,  
https://github.com/helipilot50/user-event-example-aerospike.git. 


This example requires a working Java development environment (Java 6 and above) including Maven (Maven 2). The Aerospike Java client will be downloaded from Maven Central as part of the build.

After cloning the repository, use maven to build the jar files. From the root directory of the project, issue the following command:
```bash
mvn clean package
```
A JAR file will be produced in the directory 'target', `user-event-example-1.0.0-full.jar`

###Using the solution

The JAR file is a runnable JAR.
```bash
java -jar user-event-example-1.0.0-full.jar 
```

These are the options:

- -h,--host <arg>  Server hostname (default: localhost)
- -p,--port <arg>  Server port (default: 3000)
- -n,--namespace   Namespace to be used (default: test)
- -l,--load        Load Data.
- -u,--usage       Print usage.




##Discussion

This code example is written in Java and has 2 major functions:

1. Generate data using the -l command line argument. It will generate 1000 records, each with a up to 5000 events each. The number of events per user is a random number between 0 and 4999. The events will users and events for *Option 1* will be generated in the Set `events-map`, and the users and their events for *Option 2* will be generated in Set `events-records`.
2. Access a specific user and find the account of events for a specified campaign  or criteria, as described in the problem definition. To do this, run the example with the default arguments.

### How it works

The method `work()` executes a 3 example transactions showing different ways to determine the number of times, in the last 90 days, the user `user689` has done the `tap` action on the `birds` campaign.

The code is deliberately verbose to ensure that no details are hidden.

#### Transaction 1 - Event data in a Map 
This transaction is assuming that the event data is embedded in the user record as a Map.

All the events in the Map for `user689` are read into the application. The Map is iterated and a count the actions per campaign that match the search criteria.

This is by far the slowest approach, and it also has the draw back that elements in the event Map need to be programmatically removed after 90 days. Plus the number of events is limited by the Aerospike block size (default is 128K).

```java
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
```

#### Transaction 2 - Event data in a Map accessed via a User Defined Function (UDF)
This is similar to Transaction 1, but the algorithm for counting the events that match the criteria is done on the Aerospike node in a UDF. You will note that the Java code is much simpler.

This approach is x6 faster because the event data is not shipped to the client application for processing.


```java
/*
 * data in a map using UDF
 */
log.info("*** data as map using UDF***");
start = System.currentTimeMillis();
		
long countL = (Long) this.client.execute(null, key, "event_module", "count", Value.get(action), Value.get(campaign), Value.get(timeDelta));
		
stop = System.currentTimeMillis();
log.info(String.format("%d, in %dms", countL, (stop-start)));
```

The UDF code is located in the file `event_module.lua`. You can register the UDF module with the cluster before it can be used, you can usq aql or ascli to do this, but in this example it is registered with the cluster with this code snippet:
```java
RegisterTask rt = as.client.register(null, "udf/event_module.lua", "event_module.lua", Language.LUA);
rt.waitTillComplete();

```
The UDF module is where all the algorithmic work is done to determine the count. It is the same algorithm as used in Transaction 1, but coded in Lua.

```lua
local function split(str,sep)
    local array = {}
    local reg = string.format("([^%s]+)",sep)
    for mem in string.gmatch(str,reg) do
        table.insert(array, mem)
    end
    return array
end

function count(tuple, action, campaign, period)
  local count = 0
  if aerospike:exists(tuple) then
    local events = tuple["events"] 
    for i, v in map.pairs(events) do
      if i > period then
        local parts = split(v, ":")
        local action_part = parts[2]
        local campaign_part = parts[1]
        if action_part == action and campaign_part == campaign then
          count = count + 1 
        end
      end
    end
  end 
  return count
end

```

#### Transaction 3 - Event data stored in separate records using a composite key
This transaction uses a counter in the user record and a composite key for the event comprising the userid and the counter. For example, the key for the first event would be `user689:1` and the 800th event would be `user689:800`

This approach is about as fast as Transaction 1. It has the advantage of unlimited events and because the events are individual records, the can have their own time-to-live and will auto delete after their time to live.

```java
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
```

### Output
Running the example on my development laptop against a single node Aerospike cluster running in Virtual Box, give the following output:

```
How many times did a user user689 did action tap on campaign birds in the past 90 days?
*** data as map ***
1, in 13ms
*** data as map using UDF***
1, in 2ms
*** data as records ***
1, in 11ms
```

Using the UDF is much faster on the events stored in a Map, using individual records for events gives you unlimited events and auto expiry (deletes) of records using a time-to-live for each record.
  

