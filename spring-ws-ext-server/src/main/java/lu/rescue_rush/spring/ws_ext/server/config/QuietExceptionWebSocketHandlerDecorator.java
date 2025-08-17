package lu.rescue_rush.spring.ws_ext.server.config;

import java.util.logging.Logger;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

public class QuietExceptionWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

	public static boolean DEBUG = System
			.getProperty("lu.rescue_rush.spring.ws_ext.config.QuietExceptionWebSocketHandlerDecorator.DEBUG", "false")
			.equalsIgnoreCase("true");

	private static final Logger LOGGER = Logger.getLogger(QuietExceptionWebSocketHandlerDecorator.class.getName());

	public QuietExceptionWebSocketHandlerDecorator(WebSocketHandler delegate) {
		super(delegate);
	}

	@Override
	public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
		try {
			super.handleMessage(session, message);
		} catch (Exception ex) {
			if (ex instanceof ResponseStatusException) {
				LOGGER.warning("Handled error: " + ex.getMessage() + " (" + ex.getClass().getSimpleName() + ")");
				if (DEBUG) {
					ex.printStackTrace();
				}
			} else {
				final Throwable rootCause = getRootCause(ex);
				LOGGER
						.warning("WebSocket message handling failed: " + ex.getMessage() + " (" + ex.getClass().getSimpleName() + ") -> "
								+ rootCause.getMessage() + " (" + rootCause.getClass().getSimpleName() + ")");
				if (DEBUG) {
					ex.printStackTrace();
				}
			}

			try {
				session.close(CloseStatus.SERVER_ERROR);
			} catch (Exception e) {
				LOGGER.severe("Couldn't close websocket session: " + e.getMessage() + " (" + ex.getClass().getSimpleName() + ")");
				if (DEBUG) {
					ex.printStackTrace();
				}
			}
		}
	}

	private Throwable getRootCause(Throwable ex) {
		if (ex == null) {
			return null;
		}
		while (ex.getCause() != null && ex.getCause() != ex) {
			ex = ex.getCause();
		}
		return ex;
	}

}