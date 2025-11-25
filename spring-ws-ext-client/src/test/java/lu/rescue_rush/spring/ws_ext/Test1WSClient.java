package lu.rescue_rush.spring.ws_ext;

import java.net.URI;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.socket.WebSocketHttpHeaders;

import lu.rescue_rush.spring.ws_ext.client.WSExtClientHandler;
import lu.rescue_rush.spring.ws_ext.client.annotations.WSPersistentConnection;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSMapping;
import lu.rescue_rush.spring.ws_ext.server.Test1WSServer;

@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS) // for testing purposes
@WSPersistentConnection
@WSMapping(path = "/test1")
public class Test1WSClient extends WSExtClientHandler {

	private static final Logger LOGGER = Logger.getLogger(Test1WSClient.class.getName());

	/** force the server to start before the client */
	@Autowired
	private Test1WSServer server;

	@Override
	public void init() {
		super.GLOBAL_DEBUG = true;
		LOGGER.info("Test1WS (client) initialized");
	}

	@Override
	public void onConnect(WebSocketSessionData sessionData) {
		// super.send("/ping", "test");
	}

	@WSMapping(path = "/pong")
	public void ping(WebSocketSessionData data, String message) {
		LOGGER.info("Pong received from server: " + message);
	}

	@WSMapping(path = "/test")
	public void test(WebSocketSessionData data, String message) {
		LOGGER.info("Test received from server: " + message);
	}

	@Override
	public WebSocketHttpHeaders buildHttpHeaders() {
		return new WebSocketHttpHeaders();
	}

	@Override
	public URI buildRemoteURI() {
		return URI.create("ws://localhost:1443/");
	}

}
