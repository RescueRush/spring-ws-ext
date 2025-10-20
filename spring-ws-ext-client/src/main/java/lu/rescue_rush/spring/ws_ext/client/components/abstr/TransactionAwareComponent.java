package lu.rescue_rush.spring.ws_ext.client.components.abstr;

import lu.rescue_rush.spring.ws_ext.client.WSExtClientHandler.WebSocketSessionData;
import lu.rescue_rush.spring.ws_ext.common.MessageData;
import lu.rescue_rush.spring.ws_ext.common.MessageData.TransactionDirection;

public interface TransactionAwareComponent extends WSExtClientComponent {

	void onTransaction(WebSocketSessionData sessionData, TransactionDirection dir, MessageData in, MessageData out);

}
