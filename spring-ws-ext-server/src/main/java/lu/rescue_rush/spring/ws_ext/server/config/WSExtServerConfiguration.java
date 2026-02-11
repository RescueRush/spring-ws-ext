package lu.rescue_rush.spring.ws_ext.server.config;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerMappingRegistry;
import lu.rescue_rush.spring.ws_ext.server.abstr.WSHandshakeInterceptor;

@AutoConfiguration
@EnableWebSocket
@ComponentScan(basePackageClasses = WSExtServerHandler.class)
public class WSExtServerConfiguration implements WebSocketConfigurer {

	private static final Logger LOGGER = Logger.getLogger(WSExtServerConfiguration.class.getName());

	@Autowired
	private WSExtServerMappingRegistry registry;

	@Autowired
	private WSHandshakeInterceptor handshakeInterceptor;

	@Autowired(required = false)
	private CorsConfiguration corsConfiguration;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry handlerRegistry) {
		final String[] allowedOrigins;
		final String[] allowedOriginPatterns;

		if (corsConfiguration == null) {
			LOGGER.warning("No CORS Configuration defined, using allowed origin '*'.");
			allowedOriginPatterns = new String[0];
			allowedOrigins = new String[0];
		} else if (corsConfiguration.getAllowedOrigins() != null) {
			allowedOrigins = corsConfiguration.getAllowedOrigins().toArray(new String[0]);
			allowedOriginPatterns = new String[0];
		} else if (corsConfiguration.getAllowedOriginPatterns() != null) {
			allowedOriginPatterns = corsConfiguration.getAllowedOriginPatterns().toArray(new String[0]);
			allowedOrigins = new String[0];
		} else {
			LOGGER.warning("No allowed origins founds, using '*'.");
			allowedOriginPatterns = new String[0];
			allowedOrigins = new String[0];
		}

		for (WSExtServerHandler handlerBean : registry.getBeans().values()) {
			final WebSocketHandlerRegistration registration = handlerRegistry
					.addHandler(new QuietExceptionWebSocketHandlerDecorator(handlerBean), handlerBean.getBeanPath())
					.addInterceptors(handshakeInterceptor);

			if (allowedOriginPatterns.length > 0) {
				registration.setAllowedOriginPatterns(allowedOriginPatterns);
			} else if (allowedOrigins.length > 0) {
				registration.setAllowedOrigins(allowedOrigins);
			} else {
				registration.setAllowedOriginPatterns("*");
			}
		}

		LOGGER.info("Registered " + registry.getAllBeans().length + " Server WebSocket handlers. ["
				+ String.join(", ", registry.getBeans().keySet()) + "]");
	}

}
