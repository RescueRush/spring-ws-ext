package lu.rescue_rush.spring.ws_ext.server.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSConnectionController {

	/**
	 * This is used by {@link WSConnectionController} components that manage the lifetime of the
	 * connection in the current {@link WSExtServerHandler} bean, used mainly for authentication,
	 * throwing any exception here will disconnect the session, still prefer returning false and letting
	 * {@link WSExtServerHandler} handle the disconnection. This will also make sure that
	 * {@link #afterConnection(WebSocketSessionData)} gets called properly and the caches containing the
	 * session correctly get cleaned up.
	 * 
	 * @return whether or not the session is allowed to connect
	 */
	boolean beforeConnection(WebSocketSessionData userSession);

	/**
	 * This is used by {@link WSConnectionController} components that manage the lifetime of the
	 * connection in the current {@link WSExtServerHandler} bean. At this point the
	 * {@link WebSocketSessionData} is still valid but might (prob. always is) already be closed.
	 */
	void afterConnection(WebSocketSessionData userSession);

}
