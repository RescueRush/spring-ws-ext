package lu.rescue_rush.spring.ws_ext;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@SpringBootTest(classes = WSExtMain.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WSExtTest {

	@Autowired
	private WSExtMain wsExtMain;

	@LocalServerPort
	private int port;

	public StandardWebSocketClient client;
	public WebSocketSession session;

	@Test
	public void testWebSocketConnection() throws InterruptedException, ExecutionException, IOException, JSONException {
		client = new StandardWebSocketClient();
		session = client.execute(new WebSocketHandler() {
			@Override
			public void afterConnectionEstablished(WebSocketSession session) throws Exception {
				System.out.println("Connected");
			}

			@Override
			public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
				System.out.println("Received: " + message.getPayload());
			}

			@Override
			public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
				exception.printStackTrace();
			}

			@Override
			public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
				System.out.println("Closed: " + closeStatus);
			}

			@Override
			public boolean supportsPartialMessages() {
				return false;
			}
		}, "ws://localhost:" + port + "/test1").get();
		
		System.out.println("WebSocket session established: " + session.isOpen());
		assert session.isOpen() : "WebSocket session should be open";

		final String msg = new JSONObject().put("destination", "/ping").put("payload", "hayyyy :3").toString();
		session.sendMessage(new TextMessage(msg));
		System.out.println("Message sent: "+msg);
		
		Thread.sleep(2000); // Wait for the message to be processed
	}

}
