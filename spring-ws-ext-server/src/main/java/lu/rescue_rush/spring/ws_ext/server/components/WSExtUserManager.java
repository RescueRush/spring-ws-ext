package lu.rescue_rush.spring.ws_ext.server.components;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;
import lu.rescue_rush.spring.ws_ext.server.abstr.UserID;
import lu.rescue_rush.spring.ws_ext.server.abstr.WSUserResolver;
import lu.rescue_rush.spring.ws_ext.server.components.abstr.ConnectionAwareComponent;
import lu.rescue_rush.spring.ws_ext.server.components.abstr.GenericWSExtComponent;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WSExtUserManager extends GenericWSExtComponent implements ConnectionAwareComponent {

	private final Map<Long, WebSocketSessionData> userSessionDatas = new ConcurrentHashMap<>();
	private final Map<String, UserID> userIDDatas = new ConcurrentHashMap<>();

	@Autowired
	private WSUserResolver userResolver;

	@Override
	public void onConnect(WebSocketSessionData sessionData) {
		if (!userResolver.isUser(sessionData)) {
			return;
		}

		final UserID userId = userResolver.resolve(sessionData);
		userIDDatas.put(sessionData.getId(), userId);
		userSessionDatas.put(userId.getId(), sessionData);
	}

	@Override
	public void onDisconnect(WebSocketSessionData sessionData) {
		if (!userResolver.isUser(sessionData)) {
			return;
		}
	
		final UserID userId = userIDDatas.remove(sessionData.getId());
		userSessionDatas.remove(userId.getId());
	}

	public UserID getUser(WebSocketSessionData sessionData) {
		return userIDDatas.get(sessionData.getId());
	}
	
	public boolean isAnonymous(WebSocketSessionData sessionData) {
		return !userResolver.isUser(sessionData);
	}
	
	public boolean isUser(WebSocketSessionData sessionData) {
		return userResolver.isUser(sessionData);
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
