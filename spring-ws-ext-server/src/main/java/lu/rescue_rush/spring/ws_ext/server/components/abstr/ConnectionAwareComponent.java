package lu.rescue_rush.spring.ws_ext.server.components.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface ConnectionAwareComponent extends WSExtServerComponent {

	void onConnect(WebSocketSessionData sessionData);

	void onDisconnect(WebSocketSessionData sessionData);

}
