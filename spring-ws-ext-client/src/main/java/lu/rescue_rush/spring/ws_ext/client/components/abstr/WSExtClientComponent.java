package lu.rescue_rush.spring.ws_ext.client.components.abstr;

import lu.rescue_rush.spring.ws_ext.client.WSExtClientHandler;

public interface WSExtClientComponent {

	void setHandlerBean(WSExtClientHandler bean);

	void init();

}
