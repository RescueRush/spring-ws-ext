package lu.rescue_rush.spring.ws_ext.client;

import lu.rescue_rush.spring.ws_ext.client.WSExtClientHandler.WebSocketSessionData;

public interface WSTransactionController {

	void beforeTransaction(WebSocketSessionData userSession);

	void afterTransaction(WebSocketSessionData userSession);

}
