package lu.rescue_rush.spring.ws_ext.server.component.factory;

import java.util.logging.Logger;

import org.springframework.stereotype.Component;

import lu.rescue_rush.spring.ws_ext.server.abstr.DynamicAutowireFactory;
import lu.rescue_rush.spring.ws_ext.server.component.WSSingleConnection;

@Component
public class WSSingleConnectionFactory implements DynamicAutowireFactory {

	private static final Logger LOGGER = Logger.getLogger(WSSingleConnectionFactory.class.getName());

	@Override
	public Class<?> getProvidedType() {
		return WSSingleConnection.class;
	}

	@Override
	public WSSingleConnection create(String name) {
		LOGGER.info("Creating WSSingleConnection with name: " + name);
		return new WSSingleConnection(name);
	}

}
