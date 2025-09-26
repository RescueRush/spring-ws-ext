package lu.rescue_rush.spring.ws_ext.server.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSConnectionController {
	
	void beforeConnection(WebSocketSessionData userSession);
	
	void afterConnection(WebSocketSessionData userSession);

}
