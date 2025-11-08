package lu.rescue_rush.spring.ws_ext.server.config;

import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerMappingRegistry;

@Configuration
public class WSExtServerMappingScanner {

	private final Logger LOGGER = Logger.getLogger(WSExtServerMappingScanner.class.getName());

	@Autowired(required = false)
	private List<WSExtServerHandler> serverHandlers;

	@Bean
	public WSExtServerMappingRegistry wsExtServerMappingRegistry() {
		final WSExtServerMappingRegistry registry = new WSExtServerMappingRegistry();

		if (serverHandlers != null) {
			serverHandlers.forEach(registry::register);
		}

		return registry;
	}

}