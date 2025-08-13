package lu.rescue_rush.spring.ws_ext.config;

import java.util.Map;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

	private static final Logger LOGGER = Logger.getLogger(AuthHandshakeInterceptor.class.getName());

	@Autowired
	private LocaleResolver localeResolver;

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes) throws Exception {
		final ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
		final HttpServletRequest httpRequest = servletRequest.getServletRequest();

		attributes.put("auth", httpRequest.getAttribute("auth"));

		attributes.put("locale", localeResolver.resolveLocale(httpRequest));

		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
	}

}
