package lu.rescue_rush.spring.ws_ext.server.abstr;

import java.util.Locale;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSLocaleResolver {

	Locale resolve(WebSocketSessionData userSession);

}
