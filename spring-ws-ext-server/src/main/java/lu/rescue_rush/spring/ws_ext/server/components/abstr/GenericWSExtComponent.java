package lu.rescue_rush.spring.ws_ext.server.components.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;

public class GenericWSExtComponent implements WSExtComponent {

	protected WSExtServerHandler bean;

	@Override
	public final void setHandlerBean(WSExtServerHandler bean) {
		this.bean = bean;
	}

	@Override
	public void init() {
	}

}
