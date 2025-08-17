package lu.rescue_rush.spring.ws_ext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Lazy;

import lu.rescue_rush.spring.ws_ext.server.Test1WSServer;

@SpringBootTest(classes = WSExtMain.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class WSExtTestClient {

	@Autowired
	private WSExtMain wsExtMain;

	@Autowired
	@Lazy
	private Test1WSClient client;

	@Autowired
	private Test1WSServer server;

	@Test
	public void test() throws InterruptedException {
		assert server != null : "Server isn't initialized.";

		Thread.sleep(5000); // wait for the server to be ready
		// the client is lazy-init. so it will be created & connected now

		System.out.println("awaiting connection");
		if (client.awaitConnection()) {
			System.out.println("got connection");
			client.send("/ping", "haiiiiii <w<");
		} else {
			System.out.println("Connection interrupted ?");
		}

		Thread.sleep(2000);
	}

}
