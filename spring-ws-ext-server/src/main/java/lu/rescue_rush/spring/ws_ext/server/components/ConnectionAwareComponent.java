package lu.rescue_rush.spring.ws_ext.server.components;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface ConnectionAwareComponent extends WSExtComponent {

	void onConnect(WebSocketSessionData sessionData);

	void onDisconnect(WebSocketSessionData sessionData);

}
