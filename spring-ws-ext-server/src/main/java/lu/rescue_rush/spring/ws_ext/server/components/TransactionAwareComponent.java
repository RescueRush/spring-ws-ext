package lu.rescue_rush.spring.ws_ext.server.components;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.MessageData;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface TransactionAwareComponent extends WSExtComponent {

	void onTransaction(WebSocketSessionData sessionData, MessageData in, MessageData out);

}
