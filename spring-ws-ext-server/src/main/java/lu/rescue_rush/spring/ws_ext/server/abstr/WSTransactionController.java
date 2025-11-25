package lu.rescue_rush.spring.ws_ext.server.abstr;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WSHandlerMethod;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public interface WSTransactionController {

	/**
	 * This gets called before any transaction ({@link WSextServerHandler#handleTextMessage incoming
	 * message}, {@link WSextServerHandler#afterConnectionEstablished connection} or
	 * {@link WSextServerHandler#afterConnectionClosed disconnection}).<br>
	 * This is used to setup any thread-bound resources like the connected user's locale or similar.
	 * Returning null during connection will close the session whereas returning null while handling a
	 * packet will internally trigger a {@code ResponseStatusException(HttpStatus.FORBIDDEN)} that will
	 * be handled by {@link WSExtServerHandler#sendError(WebSocketSessionData, Exception, JsonNode)} and
	 * subsequently by
	 * {@link WSExtServerHandler#handleMessageError(WebSocketSessionData, JsonNode, Throwable)}.<br>
	 * The return value will be ignored when disconnecting.
	 * 
	 * @return whether or not to allow the request to pass through
	 */
	boolean beforeTransaction(WebSocketSessionData userSession, Optional<WSHandlerMethod> method);

	/**
	 * This is used to unset all the variables/states set in
	 * {@link #beforeTransaction(WebSocketSessionData)} to leave the thread clean. The session might be
	 * invalidated at this point.
	 */
	void afterTransaction(WebSocketSessionData userSession);

}
