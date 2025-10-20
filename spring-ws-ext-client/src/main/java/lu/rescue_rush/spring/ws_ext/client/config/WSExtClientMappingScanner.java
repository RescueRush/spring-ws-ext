package lu.rescue_rush.spring.ws_ext.client.config;

import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lu.rescue_rush.spring.ws_ext.client.WSExtClientHandler;
import lu.rescue_rush.spring.ws_ext.client.WSExtClientMappingRegistry;

@Configuration
public class WSExtClientMappingScanner {

	private final Logger LOGGER = Logger.getLogger(WSExtClientMappingScanner.class.getName());

	@Autowired(required = false)
	private List<WSExtClientHandler> wsClientHandlers;

	@Bean
	public WSExtClientMappingRegistry wsExtClientMappingRegistry() {
		final WSExtClientMappingRegistry registry = new WSExtClientMappingRegistry();

		if (wsClientHandlers != null) {
			wsClientHandlers.forEach(registry::register);
		}

		return registry;
	}

}