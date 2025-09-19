package lu.rescue_rush.spring.ws_ext.server;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;

import lu.rescue_rush.spring.ws_ext.common.annotations.WSMapping;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSResponseMapping;
import lu.rescue_rush.spring.ws_ext.server.annotations.AllowAnonymous;
import lu.rescue_rush.spring.ws_ext.server.components.WSExtScheduler;

@AllowAnonymous
@WSMapping(path = "/test1")
public class Test1WSServer extends WSExtServerHandler {

	private static final Logger LOGGER = Logger.getLogger(Test1WSServer.class.getName());

	@Autowired
	private WSExtScheduler wsExtScheduler;

	@Override
	public void init() {
		LOGGER.info("Test1WS (server) initialized");
		super.DEBUG = true;
	}

	@Override
	public void onConnect(WebSocketSessionData sessionData) {
		LOGGER.info("Sent test from server.");
		super.send(sessionData, "/test", "test from server to client");
		wsExtScheduler.scheduleTask(sessionData, () -> {
			System.out.println("Scheduled task executed!");
		}, "next", 200, TimeUnit.MILLISECONDS);
	}

	@AllowAnonymous
	@WSMapping(path = "/ping")
	@WSResponseMapping(path = "/pong")
	public String ping(WebSocketSessionData sessionData, String message) {
		LOGGER.info("Ping received from " + sessionData.getSession().getId() + ": " + message);
		return "pong: " + message;
	}

}
