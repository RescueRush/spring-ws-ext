package lu.rescue_rush.spring.ws_ext.client.config;

import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import jakarta.annotation.PostConstruct;
import lu.rescue_rush.spring.ws_ext.client.WSExtClientHandler;
import lu.rescue_rush.spring.ws_ext.client.WSExtClientMappingRegistry;

@AutoConfiguration
@ComponentScan(basePackageClasses = WSExtClientHandler.class)
public class WSExtClientConfiguration {

	private static final Logger LOGGER = Logger.getLogger(WSExtClientConfiguration.class.getName());

	@Autowired
	private WSExtClientMappingRegistry registry;

	@PostConstruct
	public void init() {
		for (WSExtClientHandler wsHandlerData : registry.getBeans().values()) {
			if (wsHandlerData.isPersistentConnection()) {
				wsHandlerData.scheduleStart();
			}
		}

		LOGGER.info(
				"Registered " + registry.getAllBeans().length + " Client WebSocket handlers. [" + registry.getBeans()
						.values().stream().map(WSExtClientHandler::getWsPath).collect(Collectors.joining(", ")) + "]");
	}

}
