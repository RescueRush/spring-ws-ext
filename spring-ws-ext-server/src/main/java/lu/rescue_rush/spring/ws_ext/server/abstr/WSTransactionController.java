package lu.rescue_rush.spring.ws_ext.server.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSTransactionController {

	/**
	 * This gets called before any transaction ({@link WSextServerHandler#handleTextMessage incoming
	 * message}, {@link WSextServerHandler#afterConnectionEstablished connection} or
	 * {@link WSextServerHandler#afterConnectionClosed disconnection}).<br>
	 * This is used to setup any thread-bound resources like the connected user's locale or similar. You
	 * can use {@link WebSocketSessionData#hasRequestPath} to check if the method is being called while
	 * handling a packet.
	 */
	void beforeTransaction(WebSocketSessionData userSession);

	/**
	 * This is used to unset all the variables/states set in
	 * {@link #beforeTransaction(WebSocketSessionData)} to leave the thread clean. The session might be
	 * invalidated at this point.
	 */
	void afterTransaction(WebSocketSessionData userSession);

}
