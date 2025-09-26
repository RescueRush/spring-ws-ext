package lu.rescue_rush.spring.ws_ext.server.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSUserResolver extends WSPrincipalResolver {

	default boolean isAnonymous(WebSocketSessionData sessionData) {
		return !isUser(sessionData);
	}

	boolean isUser(WebSocketSessionData sessionData);

	UserID resolveUser(WebSocketSessionData sessionData);

}
