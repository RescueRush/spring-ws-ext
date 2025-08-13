package lu.rescue_rush.spring.ws_ext;

import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import lu.rescue_rush.spring.ws_ext.WebSocketHandlerExt.WebSocketSessionData;
import lu.rescue_rush.spring.ws_ext.annotations.AllowAnonymous;
import lu.rescue_rush.spring.ws_ext.annotations.WSMapping;

@AllowAnonymous
@WSMapping(path = "/test1")
public class Test1WS extends WSExtHandler {

	private static final Logger LOGGER = Logger.getLogger(Test1WS.class.getName());

	@PostConstruct
	public void init() {
		LOGGER.info("Test1WS initialized");
	}

	@Override
	public void onConnect(WebSocketSessionData sessionData) {
		super.getWebSocketHandler().DEBUG = true;
		super.send(sessionData, "/test", "test");
	}
	
	@AllowAnonymous
	@WSMapping(path = "/ping")
	public String ping(WebSocketSessionData sessionData, String message) {
		LOGGER.info("Ping received from " + sessionData.getSession().getId());
		return "pong: " + message;
	}

}
