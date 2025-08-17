package lu.rescue_rush.spring.ws_ext.server;

import java.util.logging.Logger;

import lu.rescue_rush.spring.ws_ext.common.annotations.WSMapping;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSResponseMapping;
import lu.rescue_rush.spring.ws_ext.server.WebSocketExtServerHandler.WebSocketSessionData;
import lu.rescue_rush.spring.ws_ext.server.annotations.AllowAnonymous;

@AllowAnonymous
@WSMapping(path = "/test1")
public class Test1WSServer extends WSExtServerHandler {

	private static final Logger LOGGER = Logger.getLogger(Test1WSServer.class.getName());

	@Override
	public void init() {
		LOGGER.info("Test1WS (server) initialized");
	}

	@Override
	public void onConnect(WebSocketSessionData sessionData) {
		super.getWebSocketHandler().DEBUG = true;
		LOGGER.info("Sent test from server.");
		super.send(sessionData, "/test", "test from server to client");
	}

	@AllowAnonymous
	@WSMapping(path = "/ping")
	@WSResponseMapping(path = "/pong")
	public String ping(WebSocketSessionData sessionData, String message) {
		LOGGER.info("Ping received from " + sessionData.getSession().getId() + ": " + message);
		return "pong: " + message;
	}

}
