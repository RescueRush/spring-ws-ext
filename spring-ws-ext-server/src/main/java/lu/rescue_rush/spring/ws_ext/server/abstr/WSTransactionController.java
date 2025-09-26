package lu.rescue_rush.spring.ws_ext.server.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSTransactionController {
	
	void beforeTransaction(WebSocketSessionData userSession);
	
	void afterTransaction(WebSocketSessionData userSession);

}
