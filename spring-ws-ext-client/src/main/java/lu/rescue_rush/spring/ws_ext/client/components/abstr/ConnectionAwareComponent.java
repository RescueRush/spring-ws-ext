package lu.rescue_rush.spring.ws_ext.client.components.abstr;

import lu.rescue_rush.spring.ws_ext.client.WSExtClientHandler.WebSocketSessionData;

public interface ConnectionAwareComponent extends WSExtClientComponent {

	void onConnect(WebSocketSessionData sessionData);

	void onDisconnect(WebSocketSessionData sessionData);

}
