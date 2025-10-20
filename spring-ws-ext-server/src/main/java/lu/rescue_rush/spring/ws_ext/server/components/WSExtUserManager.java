package lu.rescue_rush.spring.ws_ext.server.components;

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
import lu.rescue_rush.spring.ws_ext.server.components.abstr.ConnectionAwareComponent;
import lu.rescue_rush.spring.ws_ext.server.components.abstr.GenericWSExtComponent;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WSExtUserManager extends GenericWSExtComponent implements ConnectionAwareComponent {

	public static final String DEBUG_PROPERTY = WSExtUserManager.class.getName() + ".debug";
	public static boolean DEBUG = Boolean.getBoolean(DEBUG_PROPERTY);

	private final static Logger STATIC_LOGGER = Logger.getLogger(WSExtUserManager.class.getName());
	private final Map<Long, WebSocketSessionData> userSessionDatas = new ConcurrentHashMap<>();

	private Logger LOGGER;

	@Autowired
	private WSUserResolver userResolver;

	@Override
	public void init() {
		DEBUG |= super.bean.DEBUG;
		LOGGER = Logger.getLogger(WSExtUserManager.class.getSimpleName() + " # " + super.bean.getBeanPath());
	}

	@Override
	public void onConnect(WebSocketSessionData sessionData) {
		if (!userResolver.isUser(sessionData)) {
			return;
		}

		final UserID userId = userResolver.resolveUser(sessionData);

		if (userSessionDatas.containsKey(userId.getId())) {
			// user already connected, disconnect previous session
			final WebSocketSessionData previousSession = userSessionDatas.get(userId.getId());
			try {
				previousSession.getSession().close(CloseStatus.POLICY_VIOLATION);
			} catch (IOException e) {
				LOGGER.warning("Failed to disconnect previous session for user ID " + userId.getId() + ": " + e);
				if (DEBUG) {
					e.printStackTrace();
				}
			}
		}

		userSessionDatas.put(userId.getId(), sessionData);
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

	public WebSocketSessionData getUserSession(UserID ud) {
		return userSessionDatas.get(ud.getId());
	}

	public WebSocketSessionData getUserSession(Long ud) {
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
