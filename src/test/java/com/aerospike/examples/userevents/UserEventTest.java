package com.aerospike.examples.userevents;

import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.aerospike.client.AerospikeClient;

public class UserEventTest {

	AerospikeClient client;
	UserEvent subject;

	@Before
	public void setUp() throws Exception {
		client = new AerospikeClient("localhost", 3000);
		subject = new UserEvent(client, "test");
		subject.registerUDF();
	}

	@After
	public void tearDown() throws Exception {
		client.close();
	}

	@Test
	public void testUserEvent() throws Exception {
		subject.work();
	}

}
