package lu.rescue_rush.spring.ws_ext.server;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSExtServer {

	void init();

	void onConnect(WebSocketSessionData sessionData);

	void onDisconnect(WebSocketSessionData sessionData);

}
