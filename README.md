# User Event Example

## Problem
You store a number of events, related to a specific user ID, for a period of time. In this example, the time period is 90 days. Each event contains a time stamp, an action and a campaign (category).

In real time, you need to calculate number of times a specific user has a specific action, for a specific campaign.

What is the best way to model data for this in Aerospike?

##Solution
The event data consists of:
 - Timestamp - standard Unix timestamp
 - Action - the action taken by the user
 - Campaign (or category) - the capign related to the action and timestamp
  
The data is a master detail relation ship where the user has a cardinality  There are a few ways to model the event data in Aerospike, here are tow

 - As a Map stored in a Bin
 - AS separate records using a composite key.
 
As a Map


The source code for this solution is available on GitHub, and the README.md 
http://github.com/some place. 


##Discussion
