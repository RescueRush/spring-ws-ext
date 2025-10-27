package lu.rescue_rush.spring.ws_ext.server;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

import lu.rescue_rush.spring.ws_ext.common.annotations.WSMapping;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSResponseMapping;
import lu.rescue_rush.spring.ws_ext.server.annotation.AllowAnonymous;
import lu.rescue_rush.spring.ws_ext.server.component.WSScheduler;

@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS) // for testing purposes
@AllowAnonymous
@WSMapping(path = "/test1")
public class Test1WSServer extends WSExtServerHandler {

	private static final Logger LOGGER = Logger.getLogger(Test1WSServer.class.getName());

	@Autowired
	private WSScheduler wsExtScheduler;

	@Override
	public void init() {
		LOGGER.info("Test1WS (server) initialized");
		super.DEBUG = true;
	}

	@Override
	public void onConnect(WebSocketSessionData sessionData) {
		LOGGER.info("Sent test from server.");
		sessionData.send("/test", "test from server to client");
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
