package lu.rescue_rush.spring.ws_ext.config;

import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lu.rescue_rush.spring.ws_ext.WSMappingRegistry;
import lu.rescue_rush.spring.ws_ext.WSMappingRegistry.WSHandlerData;

@Configuration
@EnableWebSocket
@Controller
@Profile("!debug")
public class WSExtConfiguration implements WebSocketConfigurer {

	private static final Logger LOGGER = Logger.getLogger(WSExtConfiguration.class.getName());

	@Autowired
	private WSMappingRegistry registry;

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

		LOGGER.info("Registered " + registry.getAllBeans().length + " WebSocket handlers. [" + registry.getBeans().values().stream().map(WSHandlerData::path).collect(Collectors.joining(", ")) + "]");
	}

}
