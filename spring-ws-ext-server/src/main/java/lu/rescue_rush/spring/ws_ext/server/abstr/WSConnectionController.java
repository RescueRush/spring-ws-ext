package lu.rescue_rush.spring.ws_ext.server.abstr;

import org.springframework.web.socket.CloseStatus;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSConnectionController {

	/**
	 * This is used by {@link WSConnectionController} components that manage the lifetime of the
	 * connection in the current {@link WSExtServerHandler} bean, used mainly for authentication,
	 * throwing any exception here will disconnect the session, still prefer using
	 * {@link WebSocketSessionData#close(CloseStatus)} and explicitly state the reason for
	 * disconnection. This will also make sure that {@link #afterConnection(WebSocketSessionData)} gets
	 * called properly and the caches containing the session correctly get cleaned up.
	 */
	void beforeConnection(WebSocketSessionData userSession);

	/**
	 * This is used by {@link WSConnectionController} components that manage the lifetime of the
	 * connection in the current {@link WSExtServerHandler} bean, used mainly for authentication. At
	 * this point the {@link WebSocketSessionData} is still valid but might (prob. always is) already be
	 * closed.
	 */
	void afterConnection(WebSocketSessionData userSession);

}
