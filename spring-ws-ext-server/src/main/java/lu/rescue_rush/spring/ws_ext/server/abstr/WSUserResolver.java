package lu.rescue_rush.spring.ws_ext.server.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSUserResolver {

	boolean isUser(WebSocketSessionData sessionData);
	
	UserID resolve(WebSocketSessionData sessionData);

}
