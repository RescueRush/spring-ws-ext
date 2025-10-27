package lu.rescue_rush.spring.ws_ext.server.component.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;
import lu.rescue_rush.spring.ws_ext.server.abstr.WSConnectionController;
import lu.rescue_rush.spring.ws_ext.server.abstr.WSTransactionController;

public interface ConnectionAwareComponent extends WSExtServerComponent {

	/**
	 * Gets called whenever the associated bean received an incoming connection.<br>
	 * At this point, the {@link WSConnectionController} and {@link WSTransactionController} have been
	 * invoked. Any Exception thrown here will be ignored.
	 */
	void onConnect(WebSocketSessionData sessionData);

	/**
	 * Gets called whenever a connection to the associated bean gets disconnects, whether it being from
	 * calling {@link WebSocketSessionData#close} or the {@link WSExtServerHandler#handleMessageError
	 * message handler} throwing an exception, closing the session. At this point, the
	 * {@link WSConnectionController} and {@link WSTransactionController} have been invoked. Any
	 * Exception thrown here will be ignored.
	 */
	void onDisconnect(WebSocketSessionData sessionData);

}
