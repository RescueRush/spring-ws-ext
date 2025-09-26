package lu.rescue_rush.spring.ws_ext.server.components.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.MessageData;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.MessageData.TransactionDirection;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface TransactionAwareComponent extends WSExtComponent {

	void onTransaction(WebSocketSessionData sessionData, TransactionDirection dir, MessageData in, MessageData out);

}
