package lu.rescue_rush.spring.ws_ext.server.config;

import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerMappingRegistry;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerMappingRegistry.WSHandlerData;

@AutoConfiguration
@EnableWebSocket
@ComponentScan(basePackageClasses = WSExtServerHandler.class)
public class WSExtServerConfiguration implements WebSocketConfigurer {

	private static final Logger LOGGER = Logger.getLogger(WSExtServerConfiguration.class.getName());

	@Autowired
	private WSExtServerMappingRegistry registry;

	@Autowired
	private AuthHandshakeInterceptor authHandshakeInterceptor;

	@Autowired
	private CorsConfiguration corsConfiguration;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry handlerRegistry) {
		final String[] allowedOrigins = corsConfiguration.getAllowedOrigins() == null ? new String[] { "*" }
				: corsConfiguration.getAllowedOrigins().toArray(new String[0]);

		for (WSHandlerData handlerBean : registry.getBeans().values()) {
			//@formatter:off
			handlerRegistry
					.addHandler(new QuietExceptionWebSocketHandlerDecorator(handlerBean.handler()), handlerBean.path())
					.addInterceptors(authHandshakeInterceptor)
					.setAllowedOriginPatterns(allowedOrigins);
			//@formatter:off
		}

		LOGGER.info("Registered " + registry.getAllBeans().length + " Server WebSocket handlers. [" + registry.getBeans().values().stream().map(WSHandlerData::path).collect(Collectors.joining(", ")) + "]");
	}

}
