package lu.rescue_rush.spring.ws_ext.client;

import java.net.URI;

import org.springframework.web.socket.WebSocketHttpHeaders;

import lu.rescue_rush.spring.ws_ext.client.WebSocketExtClientHandler.WebSocketSessionData;

public abstract class WSExtClientHandler {

	private WebSocketExtClientHandler webSocketHandler;

	public abstract WebSocketHttpHeaders buildHttpHeaders();

	public abstract URI buildRemoteURI();

	public void init() {
	}

	public void onConnect(WebSocketSessionData sessionData) {
	}

	public void onDisconnect(WebSocketSessionData sessionData) {
	}

	public void send(String destination, String packetId, Object payload) {
		webSocketHandler.send(destination, packetId, payload);
	}

	public void send(String destination, Object payload) {
		webSocketHandler.send(destination, payload);
	}

	public WebSocketExtClientHandler getWebSocketHandler() {
		return webSocketHandler;
	}

	public void setWebSocketHandler(WebSocketExtClientHandler webSocketHandler) {
		this.webSocketHandler = webSocketHandler;
	}
	
	public boolean awaitConnection() {
		return webSocketHandler.awaitConnection();
	}

}
