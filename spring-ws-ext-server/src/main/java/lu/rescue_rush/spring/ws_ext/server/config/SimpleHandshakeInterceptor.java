package lu.rescue_rush.spring.ws_ext.server.config;

import java.util.Map;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.socket.WebSocketHandler;

import jakarta.servlet.http.HttpServletRequest;
import lu.rescue_rush.spring.ws_ext.server.abstr.WSHandshakeInterceptor;

@Component
public class SimpleHandshakeInterceptor implements WSHandshakeInterceptor {

	private static final Logger LOGGER = Logger.getLogger(SimpleHandshakeInterceptor.class.getName());

	public static final String HTTP_ATTRIBUTE_AUTH = "auth";
	public static final String HTTP_ATTRIBUTE_LOCALE = "locale";

	@Autowired
	private LocaleResolver localeResolver;

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) throws Exception {
		final ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
		final HttpServletRequest httpRequest = servletRequest.getServletRequest();

		attributes.put(HTTP_ATTRIBUTE_AUTH, httpRequest.getAttribute(HTTP_ATTRIBUTE_AUTH));
		attributes.put(HTTP_ATTRIBUTE_LOCALE, localeResolver.resolveLocale(httpRequest));

		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
	}

}
