package lu.rescue_rush.spring.ws_ext.server.components;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.MessageData;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface MessageAwareComponent extends WSExtComponent {

	void onMessage(WebSocketSessionData sessionData, MessageData out);

}
