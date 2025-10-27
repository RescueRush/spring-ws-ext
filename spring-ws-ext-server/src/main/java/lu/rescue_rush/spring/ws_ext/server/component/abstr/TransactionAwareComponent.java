package lu.rescue_rush.spring.ws_ext.server.component.abstr;

import lu.rescue_rush.spring.ws_ext.common.MessageData;
import lu.rescue_rush.spring.ws_ext.common.MessageData.TransactionDirection;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface TransactionAwareComponent extends WSExtServerComponent {

	void onTransaction(WebSocketSessionData sessionData, TransactionDirection dir, MessageData in, MessageData out);

}
