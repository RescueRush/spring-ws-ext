package lu.rescue_rush.spring.ws_ext.server.component.abstr;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;

public class GenericWSExtServerComponent implements WSExtServerComponent {

	protected WSExtServerHandler bean;

	@Override
	public final void setHandlerBean(WSExtServerHandler bean) {
		this.bean = bean;
	}

	@Override
	public void init() {
	}

}
