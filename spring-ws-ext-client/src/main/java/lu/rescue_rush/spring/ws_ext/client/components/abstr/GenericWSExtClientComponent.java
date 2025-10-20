package lu.rescue_rush.spring.ws_ext.client.components.abstr;

import lu.rescue_rush.spring.ws_ext.client.WSExtClientHandler;

public class GenericWSExtClientComponent implements WSExtClientComponent {

	protected WSExtClientHandler bean;

	@Override
	public final void setHandlerBean(WSExtClientHandler bean) {
		this.bean = bean;
	}

	@Override
	public void init() {
	}

}
