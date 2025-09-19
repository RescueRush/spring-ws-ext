package lu.rescue_rush.spring.ws_ext.server.components;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSExtComponent {

	void setHandlerBean(WSExtServerHandler bean);
	
	void init();
	
	void onConnect(WebSocketSessionData sessionData);
	
	void onDisconnect(WebSocketSessionData sessionData);
	
}
