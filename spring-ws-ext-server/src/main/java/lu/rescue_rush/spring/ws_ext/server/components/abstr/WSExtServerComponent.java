package lu.rescue_rush.spring.ws_ext.server.components.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;

public interface WSExtServerComponent {

	void setHandlerBean(WSExtServerHandler bean);

	void init();

}
