package lu.rescue_rush.spring.ws_ext.server.components;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

public class GenericWSExtComponent implements WSExtComponent {

	protected WSExtServerHandler bean;

	@Override
	public final void setHandlerBean(WSExtServerHandler bean) {
		this.bean = bean;
	}

	@Override
	public void init() {
	}

	@Override
	public void onConnect(WebSocketSessionData sessionData) {
	}

	@Override
	public void onDisconnect(WebSocketSessionData sessionData) {
	}

}
