package lu.rescue_rush.spring.ws_ext.server.component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;
import lu.rescue_rush.spring.ws_ext.server.abstr.UserID;
import lu.rescue_rush.spring.ws_ext.server.abstr.WSUserResolver;
import lu.rescue_rush.spring.ws_ext.server.component.abstr.ConnectionAwareComponent;
import lu.rescue_rush.spring.ws_ext.server.component.abstr.WSExtServerComponent;

public class WSSingleConnection implements WSExtServerComponent, ConnectionAwareComponent {

	public static final String DEBUG_PROPERTY = WSSingleConnection.class.getName() + ".debug";
	public static boolean DEBUG = Boolean.getBoolean(DEBUG_PROPERTY);

	protected final static Logger LOGGER = Logger.getLogger(WSSingleConnection.class.getName());

	protected final String poolName;
	protected Set<String> handlingPaths = new HashSet<>();

	protected Map<Long, WebSocketSessionData> connectedSessions = new ConcurrentHashMap<>();
	protected Map<String, WSUserManager> userManagers = new ConcurrentHashMap<>();

	@Autowired
	private WSUserResolver userResolver;

	public WSSingleConnection(String poolName) {
		this.poolName = poolName;
	}

	@Override
	public void onConnect(WebSocketSessionData sessionData) {
		if (!userResolver.isUser(sessionData)) {
			return;
		}

		final UserID user = userResolver.resolveUser(sessionData);
		final WSExtServerHandler bean = sessionData.getWsHandlerBean();

		if (connectedSessions.containsKey(user.getId())) {
			// user already connected, disconnect previous session
			final WebSocketSessionData previousSession = connectedSessions.get(user.getId());
			final WSExtServerHandler previousBean = previousSession.getWsHandlerBean();
			if (DEBUG) {
				LOGGER
						.warning("User " + user + " is already connected in pool " + poolName + "[" + previousBean.getBeanPath()
								+ "], closing old connection.");
			}

			if (previousSession.isOpen()) {
				try {
					previousSession.close(CloseStatus.POLICY_VIOLATION);
				} catch (IOException e) {
					LOGGER
							.warning("Error while closing previous connection for user " + user + " in pool " + poolName + "["
									+ previousBean.getBeanPath() + "]: " + e);
					if (DEBUG) {
						e.printStackTrace();
					}
				}
			} else {
				LOGGER.warning("Previous session was invalid but still registered for user ID: " + user + ", ignoring.");
			}
		}

		connectedSessions.put(user.getId(), sessionData);
	}

	@Override
	public void onDisconnect(WebSocketSessionData sessionData) {
		if (!userResolver.isUser(sessionData)) {
			return;
		}

		connectedSessions.remove(userResolver.resolveUser(sessionData).getId());
	}

	@Override
	public void init() {
	}

	@Override
	public void setHandlerBean(WSExtServerHandler bean) {
		handlingPaths.add(bean.getBeanPath());
		userManagers
				.put(bean.getBeanPath(),
						bean
								.getComponentOfType(WSUserManager.class)
								.orElseThrow(() -> new IllegalStateException("No WSUserManager found on WS: " + bean.getBeanPath())));
	}

}
