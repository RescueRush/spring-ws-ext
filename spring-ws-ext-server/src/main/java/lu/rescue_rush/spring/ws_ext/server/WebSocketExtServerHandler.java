package lu.rescue_rush.spring.ws_ext.server;

import static lu.rescue_rush.spring.ws_ext.common.WSPathUtils.normalizeURI;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerMappingRegistry.WSHandlerMethod;

@Component("server_webSocketHandlerExt")
@Scope("prototype")
public class WebSocketExtServerHandler extends TextWebSocketHandler {

	public boolean DEBUG = System.getProperty("lu.rescue_rush.ws_ext.debug", "false").equalsIgnoreCase("true");

	public static final long TIMEOUT = 60_000, // 60 seconds
			PERIODIC_CHECK_DELAY = 10_000;

	private final Logger LOGGER;

	private final String beanPath;
	private final WSExtServerHandler bean;
	private final Map<String, WSHandlerMethod> methods;

	private final Map<String, WebSocketSession> wsSessions = new ConcurrentHashMap<>();
	private final Map<String, WebSocketSessionData> wsSessionDatas = new ConcurrentHashMap<>();
	private final Map<Long, WebSocketSessionData> userSessionDatas = new ConcurrentHashMap<>();

	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

	private final Map<String, List<ScheduledTaskData<?>>> scheduledTasks = new ConcurrentHashMap<>();

	private final boolean timeout;
	private final long timeoutDelay;

	@Autowired
	private ObjectMapper objectMapper;

	public WebSocketExtServerHandler(String path, WSExtServerHandler bean, Map<String, WSHandlerMethod> methods, boolean timeout, long timeoutDelayMs) {
		this.beanPath = path;
		this.bean = bean;
		this.methods = methods.entrySet().stream().collect(Collectors.toMap(k -> normalizeURI(k.getKey()), v -> v.getValue()));
		this.timeout = timeout;
		this.timeoutDelay = timeoutDelayMs;

		this.LOGGER = Logger.getLogger("WebSocketHandler " + path);

		executorService.scheduleAtFixedRate(() -> {
			wsSessionDatas.entrySet().removeIf(entry -> {
				final WebSocketSessionData sessionData = entry.getValue();
				final WebSocketSession session = sessionData.getSession();

				if (!session.isOpen()) {
					scheduledTasks.remove(session.getId());
					try {
						if (DEBUG) {
							LOGGER
									.info("Removed invalid session (" + (sessionData.isUser() ? sessionData.getUser() : "ANONYMOUS") + " ["
											+ sessionData.getSession().getId() + "])");
						}
						sessionData.getSession().close(CloseStatus.NORMAL);
					} catch (IOException e) {
						LOGGER.warning("Failed to close session (" + sessionData.getSession().getId() + "): " + e.getMessage());
					}
					return true;
				}

				if (!sessionData.isAlive() && this.timeout) {
					scheduledTasks.remove(session.getId());
					try {
						if (DEBUG) {
							LOGGER
									.info("Removed timed out session (" + (sessionData.isUser() ? sessionData.getUser() : "ANONYMOUS")
											+ " [" + sessionData.getSession().getId() + "])");
						}
						sessionData.getSession().close(CloseStatus.NORMAL);
					} catch (IOException e) {
						LOGGER.warning("Failed to close session (" + sessionData.getSession().getId() + "): " + e.getMessage());
					}
					return true;
				}
				return false;
			});

			scheduledTasks.entrySet().forEach(e -> e.getValue().removeIf(t -> t.isCancelled() || t.isDone()));
			scheduledTasks.entrySet().removeIf(e -> e.getValue().isEmpty());
		}, PERIODIC_CHECK_DELAY, PERIODIC_CHECK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		final Authentication auth = (Authentication) session.getAttributes().get("auth");
		SecurityContextHolder.getContext().setAuthentication(auth);

		wsSessions.put(session.getId(), session);

		WebSocketSessionData sessionData;

		if (isAnonymousContext()) { // anonymous user
			sessionData = new WebSocketSessionData(session.getId(), auth, null, this.beanPath);

			wsSessionDatas.put(session.getId(), sessionData);
		} else { // logged in user
			final UserID user = (UserID) auth.getPrincipal();
			sessionData = new WebSocketSessionData(session.getId(), auth, user, this.beanPath);

			wsSessionDatas.put(session.getId(), sessionData);
			userSessionDatas.put(user.getId(), sessionData);
		}

		bean.onConnect(sessionData);
	}

	private boolean isAnonymousContext() {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken;
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		if (DEBUG) {
			LOGGER.info("[" + session.getId() + "] Received message: " + message.toString());
		}

		final JsonNode incomingJson = objectMapper.readTree(message.getPayload());
		badRequest(!incomingJson.has("destination"), session, "Invalid packet format: missing 'destination' field.", message.getPayload());
		String requestPath = incomingJson.get("destination").asText();
		final String packetId = incomingJson.has("packetId") ? incomingJson.get("packetId").asText() : null;
		final JsonNode payload = incomingJson.get("payload");

		final Authentication auth = (Authentication) session.getAttributes().get("auth");
		SecurityContextHolder.getContext().setAuthentication(auth);

		final WSHandlerMethod handlerMethod = resolveMethod(requestPath);
		badRequest(handlerMethod == null, session, "No method found for destination: " + requestPath, message.getPayload());
		final Method method = handlerMethod.method();
		badRequest(method == null, session, "No method attached for destination: " + requestPath, message.getPayload());
		final boolean returnsVoid = method.getReturnType().equals(Void.TYPE);
		requestPath = handlerMethod.inPath();
		badRequest(requestPath == null, session, "No request path defined for destination: " + requestPath, message.getPayload());
		final String responsePath = handlerMethod.outPath();

		final WebSocketSessionData userSession = wsSessionDatas.get(session.getId());
		userSession.setRequestPath(requestPath);
		userSession.setPacketId(packetId);
		userSession.lastPacket = System.currentTimeMillis();

		if (isAnonymousContext()) { // anonymous user
			LocaleContextHolder.setLocale((Locale) session.getAttributes().get("locale"));
		} else { // user
			// so that the locale can be changed from a set-lang HTTP request
			final UserID ud = (UserID) session.getAttributes().get("user");
			if (ud instanceof LocaleHolder) {
				LocaleContextHolder.setLocale(((LocaleHolder) ud).getLocale());
			}
		}

		Exception err = null;
		try {
			Object returnValue = null;

			if (method.getParameterCount() == 2) {
				final Class<?> parameterType = method.getParameterTypes()[1];
				badRequest(payload == null, session, "Payload expected for destination: " + requestPath, message.getPayload());
				final Object param = objectMapper.readValue(payload.toString(), parameterType);

				returnValue = method.invoke(bean, userSession, param);
			} else if (method.getParameterCount() == 1) {
				returnValue = method.invoke(bean, userSession);
			} else {
				LOGGER.warning("Method " + method.getName() + " has an invalid number of parameters: " + method.getParameterCount());
				return;
			}

			if (!returnsVoid) {
				final ObjectNode root = objectMapper.createObjectNode();
				root.set("payload", objectMapper.valueToTree(returnValue));
				root.put("destination", responsePath);
				if (packetId != null) {
					root.put("packetId", packetId);
				}
				final String jsonResponse = objectMapper.writeValueAsString(root);

				session.sendMessage(new TextMessage(jsonResponse));
			}
		} catch (Exception e) {
			err = e;

			final ObjectNode root = objectMapper.createObjectNode();
			root.put("status", 500);
			root.set("packet", incomingJson);

			if (e instanceof ResponseStatusException rse) {
				root.put("status", rse.getStatusCode().value());
				root.put("message", rse.getReason());
			}

			session.sendMessage(new TextMessage(root.toString()));
		} finally {
			SecurityContextHolder.clearContext();
			LocaleContextHolder.setLocale(null);
		}

		if (err != null) {
			throw err;
		}
	}

	private void badRequest(boolean b, WebSocketSession session, String msg, String content) {
		if (!b)
			return;
		if (DEBUG)
			LOGGER.warning("[" + session.getId() + "] " + msg + " (" + content + ")");
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		cancelScheduledTasks(session);

		final WebSocketSessionData sessionData = wsSessionDatas.get(session.getId());

		wsSessions.remove(session.getId());
		wsSessionDatas.remove(session.getId());

		if (sessionData.isUser())
			userSessionDatas.remove(sessionData.user.getId());

		sessionData.invalidate();

		bean.onDisconnect(sessionData);
	}

	public boolean cancelScheduledTasks(WebSocketSession session) {
		final String sessionId = session.getId();
		if (!scheduledTasks.containsKey(sessionId)) {
			return false;
		}
		scheduledTasks.get(sessionId).forEach(ScheduledTaskData::cancel);
		return !scheduledTasks.get(sessionId).isEmpty();
	}

	public boolean cancelScheduledTasks(WebSocketSessionData sessionData) {
		return cancelScheduledTasks(sessionData.getSession());
	}

	public boolean clearScheduledTasks(WebSocketSessionData sessionData, String matchingId) {
		Objects.requireNonNull(matchingId);
		return clearScheduledTasks(sessionData.getSession(), matchingId);
	}

	public boolean clearScheduledTasks(WebSocketSession session, String matchingId) {
		Objects.requireNonNull(matchingId);
		return clearScheduledTasks(session, (std) -> matchingId.equals(std.id));
	}

	public boolean clearScheduledTasks(WebSocketSession session, Predicate<ScheduledTaskData<?>> pred) {
		Objects.requireNonNull(session);
		Objects.requireNonNull(pred);
		if (!scheduledTasks.containsKey(session.getId())) {
			return false;
		}
		scheduledTasks.get(session.getId()).stream().filter(pred).forEach(t -> t.cancel());
		final boolean removed = scheduledTasks.get(session.getId()).removeIf(pred);
		return removed;
	}

	public boolean clearScheduledTasks(WebSocketSessionData sessionData, Predicate<ScheduledTaskData<?>> pred) {
		return clearScheduledTasks(sessionData.getSession(), pred);
	}

	public Collection<ScheduledTaskData<?>> getScheduledTasks(WebSocketSession session) {
		return scheduledTasks.get(session.getId());
	}

	public Collection<ScheduledTaskData<?>> getScheduledTasks(WebSocketSessionData sessionData) {
		return getScheduledTasks(sessionData.getSession());
	}

	public <T> void scheduleTask(WebSocketSessionData sessionData, Runnable run, String id, long delay, TimeUnit unit) {
		Objects.requireNonNull(sessionData);
		scheduleTask(sessionData.getSession(), run, id, delay, unit);
	}

	public <T> void scheduleTask(WebSocketSession session, Runnable run, String id, long delay, TimeUnit unit) {
		scheduleTask(session, (Callable<Void>) () -> {
			run.run();
			return null;
		}, id, delay, unit);
	}

	public <T> void scheduleTask(WebSocketSessionData sessionData, Callable<T> run, String id, long delay, TimeUnit unit) {
		Objects.requireNonNull(sessionData);
		scheduleTask(sessionData.getSession(), run, id, delay, unit);
	}

	public <T> void scheduleTask(WebSocketSession session, Callable<T> run, String id, long delay, TimeUnit unit) {
		Objects.requireNonNull(session);

		final ScheduledFuture<T> newTask = executorService.schedule(run, delay, unit);

		scheduledTasks.computeIfAbsent(session.getId(), (k) -> Collections.synchronizedList(new ArrayList<ScheduledTaskData<?>>()));
		scheduledTasks.get(session.getId()).add(new ScheduledTaskData<T>(id, newTask));
	}

	public void send(WebSocketSession session, String destination, String packetId, Object payload) {
		Objects.requireNonNull(session);
		Objects.requireNonNull(destination);

		if (session.isOpen()) {
			try {
				final String json = buildPacket(destination, packetId, payload);

				session.sendMessage(new TextMessage(json));

				wsSessionDatas.get(session.getId()).lastPacket = System.currentTimeMillis();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			LOGGER.warning("Session is closed: " + session);
		}
	}

	public void send(WebSocketSessionData sessionData, String destination, String packetId, Object payload) {
		Objects.requireNonNull(sessionData);
		send(sessionData.getSession(), destination, packetId, payload);
	}

	public void send(WebSocketSessionData sessionData, String destination, Object payload) {
		Objects.requireNonNull(sessionData);
		send(sessionData.getSession(), destination, null, payload);
	}

	public void send(WebSocketSession session, String destination, Object payload) {
		send(session, destination, null, payload);
	}

	public int broadcast(Predicate<WebSocketSessionData> predicate, String destination, Object payload) {
		int count = 0;

		final String json = buildPacket(destination, null, payload);

		for (WebSocketSessionData wsSessionData : wsSessionDatas.values()) {
			if (!predicate.test(wsSessionData)) {
				continue;
			}

			final WebSocketSession wsSession = wsSessionData.getSession();
			if (wsSession.isOpen()) {
				try {
					wsSession.sendMessage(new TextMessage(json));
				} catch (IOException e) {
					e.printStackTrace();
				}
				count++;
			} else {
				LOGGER.warning("Session is closed: " + wsSession);
			}
		}
		return count;
	}

	public int broadcast(String destination, Object payload) {
		return broadcast((s) -> true, destination, payload);
	}

	private String buildPacket(String destination, String packetId, Object payload) {
		try {
			final ObjectNode root = objectMapper.createObjectNode();
			root.set("payload", objectMapper.valueToTree(payload));
			root.put("destination", destination);
			if (packetId != null) {
				root.put("packetId", packetId);
			}
			return objectMapper.writeValueAsString(root);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Error while building packet.", e);
		}
	}

	public WSHandlerMethod resolveMethod(String requestPath) {
		requestPath = normalizeURI(requestPath);
		final List<String> matchingPatterns = new ArrayList<>();
		for (String pattern : methods.keySet()) {
			if (pathMatcher.match(pattern, requestPath)) {
				matchingPatterns.add(pattern);
			}
		}
		matchingPatterns.sort(pathMatcher.getPatternComparator(requestPath));
		String bestPattern = matchingPatterns.get(0);
		WSHandlerMethod bestMatch = methods.get(bestPattern);

		return bestMatch;
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

	public Collection<WebSocketSession> getConnectedSessions() {
		return wsSessions.values();
	}

	public Collection<WebSocketSessionData> getConnectedSessionDatas() {
		return wsSessionDatas.values();
	}

	public Collection<WebSocketSessionData> getConnectedUserSessionDatas() {
		return userSessionDatas.values();
	}

	public record ScheduledTaskData<T>(String id, ScheduledFuture<T> future) {

		public String getId() {
			return id;
		}

		public boolean isDone() {
			return future.isDone();
		}

		public boolean isCancelled() {
			return future.isCancelled();
		}

		public T get() throws InterruptedException, ExecutionException {
			return future.get();
		}

		public boolean cancel() {
			return future.cancel(false);
		}

		public boolean cancelForce() {
			return future.cancel(true);
		}

	}

	public class WebSocketSessionData {

		private String id;
		private Authentication auth;
		private UserID user;
		private long lastPacket = System.currentTimeMillis();
		private String wsPath, requestPath, packetId;

		public WebSocketSessionData(String id, Authentication auth, UserID user, String wsPath) {
			this.id = id;
			this.auth = auth;
			this.user = user;
			this.wsPath = wsPath;
		}

		public void invalidate() {
			clearContext();

			auth = null;
			id = null;
		}

		public String getId() {
			return id;
		}

		public WebSocketSession getSession() {
			return wsSessions.get(this.id);
		}

		public Authentication getAuth() {
			return auth;
		}

		public UserID getUser() {
			return user;
		}

		public long getLastPing() {
			return lastPacket;
		}

		public boolean isAlive() {
			return System.currentTimeMillis() - lastPacket < timeoutDelay;
		}

		public String getWsPath() {
			return wsPath;
		}

		public String getRequestPath() {
			return requestPath;
		}

		public void setRequestPath(String requestPath) {
			this.requestPath = requestPath;
		}

		public String getPacketId() {
			return packetId;
		}

		public void setPacketId(String packetId) {
			this.packetId = packetId;
		}

		public boolean hasPacketId() {
			return packetId != null;
		}

		public boolean isUser() {
			return user != null;
		}

		public void clearContext() {
			this.requestPath = null;
			this.packetId = null;
		}

	}

}