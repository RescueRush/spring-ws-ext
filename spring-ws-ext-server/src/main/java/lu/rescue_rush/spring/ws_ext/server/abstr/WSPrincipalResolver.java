package lu.rescue_rush.spring.ws_ext.server.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSPrincipalResolver {

	<T> T resolvePrincipal(WebSocketSessionData sessionData);

}
