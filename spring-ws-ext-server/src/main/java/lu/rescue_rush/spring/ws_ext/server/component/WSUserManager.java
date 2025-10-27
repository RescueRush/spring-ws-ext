package lu.rescue_rush.spring.ws_ext.server.component;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;
import lu.rescue_rush.spring.ws_ext.server.abstr.UserID;
import lu.rescue_rush.spring.ws_ext.server.abstr.WSUserResolver;
import lu.rescue_rush.spring.ws_ext.server.component.abstr.ConnectionAwareComponent;
import lu.rescue_rush.spring.ws_ext.server.component.abstr.GenericWSExtServerComponent;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WSUserManager extends GenericWSExtServerComponent implements ConnectionAwareComponent {

	public static final String DEBUG_PROPERTY = WSUserManager.class.getName() + ".debug";
	public static boolean DEBUG = Boolean.getBoolean(DEBUG_PROPERTY);
	private final static Logger STATIC_LOGGER = Logger.getLogger(WSUserManager.class.getName());

	private final Map<Long, WebSocketSessionData> userSessionDatas = new ConcurrentHashMap<>();
	private Logger LOGGER;

	@Autowired
	private WSUserResolver userResolver;

	@Override
	public void init() {
		DEBUG |= super.bean.DEBUG;
		LOGGER = Logger.getLogger(WSUserManager.class.getSimpleName() + " # " + super.bean.getBeanPath());
	}

	@Override
	public void onConnect(WebSocketSessionData sessionData) {
		if (!userResolver.isUser(sessionData)) {
			return;
		}

		final UserID user = userResolver.resolveUser(sessionData);

		if (userSessionDatas.containsKey(user.getId())) {
			// user already connected, disconnect previous session
			final WebSocketSessionData previousSession = userSessionDatas.get(user.getId());
			if (previousSession.isOpen()) {
				if (DEBUG) {
					LOGGER.warning("User " + user + " is already connected to " + super.bean.getBeanPath() + ", closing old connection.");
				}
				try {
					previousSession.getSession().close(CloseStatus.POLICY_VIOLATION);
				} catch (IOException e) {
					LOGGER.warning("Failed to disconnect previous session for user ID: " + user + ": " + e);
					if (DEBUG) {
						e.printStackTrace();
					}
				}
			} else {
				LOGGER.warning("Previous session was invalid but still cached for user ID: " + user + ", ignoring.");
			}
		}

		userSessionDatas.put(user.getId(), sessionData);
	}

	@Override
	public void onDisconnect(WebSocketSessionData sessionData) {
		if (!userResolver.isUser(sessionData)) {
			return;
		}

		final UserID userId = userResolver.resolveUser(sessionData);
		userSessionDatas.remove(userId.getId());
	}

	public boolean hasUserSession(UserID ud) {
		return userSessionDatas.containsKey(ud.getId());
	}

	public boolean hasUserSession(Long ud) {
		return userSessionDatas.containsKey(ud);
	}

	public void checkStatus(UserID ud) {
		checkStatus(ud.getId());
	}

	public void checkStatus(long ud) {
		if (!hasUserSession(ud)) {
			return;
		}

		try {
			userSessionDatas.get(ud).checkStatus();
		} catch (IOException e) {
			LOGGER.severe("Couldn't check session's status for user: " + ud + ": " + e);
			if (DEBUG) {
				e.printStackTrace();
			}
		}
	}

	public WebSocketSessionData getUserSession(UserID ud) {
		return userSessionDatas.get(ud.getId());
	}

	public WebSocketSessionData getUserSession(long ud) {
		return userSessionDatas.get(ud);
	}

	public List<WebSocketSessionData> getUserSessions(Set<Long> ids) {
		return ids.stream().map(userSessionDatas::get).filter(Objects::nonNull).toList();
	}

	public Collection<WebSocketSessionData> getConnectedUserSessionDatas() {
		return userSessionDatas.values();
	}

	public int getConnectedUserCount() {
		return userSessionDatas.size();
	}

	public int getConnectedAnonymousCount() {
		return super.bean.getConnectedSessionCount() - userSessionDatas.size();
	}

}
