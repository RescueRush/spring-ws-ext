package lu.rescue_rush.spring.ws_ext.server;

import static lu.rescue_rush.spring.ws_ext.common.WSPathUtils.normalizeURI;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;

import jakarta.annotation.PostConstruct;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSMapping;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSResponseMapping;
import lu.rescue_rush.spring.ws_ext.server.annotations.AllowAnonymous;
import lu.rescue_rush.spring.ws_ext.server.annotations.WSTimeout;
import lu.rescue_rush.spring.ws_ext.server.components.WSExtComponent;

public class WSExtServerHandler extends TextWebSocketHandler {

	public static final String DEBUG_PROPERTY = WSExtServerHandler.class.getSimpleName() + ".debug";
	public boolean DEBUG = Boolean.getBoolean(DEBUG_PROPERTY);

	public static final long TIMEOUT = 60_000, // 60 seconds
			PERIODIC_CHECK_DELAY = 10_000;

	private static final Logger STATIC_LOGGER = Logger.getLogger(WSExtServerHandler.class.getName());

	private final Logger LOGGER;

	@Autowired
	private WSExtServerHandler bean;
	private final String beanPath;
	private final Map<String, WSHandlerMethod> methods;

	private final Map<String, WebSocketSession> wsSessions = new ConcurrentHashMap<>();
	private final Map<String, WebSocketSessionData> wsSessionDatas = new ConcurrentHashMap<>();
	private final Map<Long, WebSocketSessionData> userSessionDatas = new ConcurrentHashMap<>();

	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

	private WSExtComponent[] components;

	private final boolean timeout;
	private final long timeoutDelay;

	@Autowired
	private ObjectMapper objectMapper;

	@SuppressWarnings("unused")
	public WSExtServerHandler() {
		scan: {
			final Object bean = this;
			final Class<?> target = this.getClass();

			final WSMapping beanMapping = target.getAnnotation(WSMapping.class);
			if (beanMapping == null) {
				throw new IllegalStateException("Bean " + target.getName() + " is missing @WSMapping annotation.");
			}

			final WSTimeout timeout = target.getAnnotation(WSTimeout.class);

			final Map<String, WSHandlerMethod> methods = new ConcurrentHashMap<>();

			for (Method targetMethod : target.getDeclaredMethods()) {
				if (!targetMethod.isAnnotationPresent(WSMapping.class)) {
					continue;
				}

				final WSMapping mapping = targetMethod.getAnnotation(WSMapping.class);
				final WSResponseMapping responseMapping = targetMethod.getAnnotation(WSResponseMapping.class);
				final AllowAnonymous allowAnonymous = targetMethod.getAnnotation(AllowAnonymous.class);

				final String inPath = normalizeURI(mapping.path());
				final String outPath = normalizeURI(responseMapping == null ? mapping.path() : responseMapping.path());
				final boolean allowAnonymousFlag = allowAnonymous != null;

				methods.put(mapping.path(), new WSHandlerMethod(targetMethod, inPath, outPath, allowAnonymousFlag));
			}

			this.beanPath = beanMapping.path();
			this.methods = methods.entrySet().stream()
					.collect(Collectors.toMap(k -> normalizeURI(k.getKey()), v -> v.getValue()));
			this.timeout = timeout != null ? timeout.value() : true;
			this.timeoutDelay = timeout != null ? timeout.timeout() : WSExtServerHandler.TIMEOUT;
		}

		this.LOGGER = Logger.getLogger("WebSocketHandler " + beanPath);

		schedulePeriodicCleanup();
	}

	@PostConstruct
	private void init_() {
		// all the beans have been injected at this point
		scan: {
			final Object bean = this;
			final Class<?> target = this.getClass();

			final List<WSExtComponent> components = new ArrayList<>();

			for (Field f : target.getDeclaredFields()) {
				if (WSExtComponent.class.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					try {
						final WSExtComponent comp = (WSExtComponent) f.get(bean);
						if (comp != null) {
							components.add(comp);
							comp.setHandlerBean(this);
							comp.init();
						}
					} catch (IllegalArgumentException | IllegalAccessException e) {
						LOGGER.warning("Failed to access WSExtComponent field " + f.getName() + ": " + e.getMessage());
					}
				}
			}

			this.components = components.toArray(new WSExtComponent[0]);
		}

		init();
	}

	private void schedulePeriodicCleanup() {
		// remove closed or timed out sessions
		executorService.scheduleAtFixedRate(() -> wsSessionDatas.entrySet().removeIf(entry -> {
			final WebSocketSessionData sessionData = entry.getValue();
			final WebSocketSession session = sessionData.getSession();
			if (!session.isOpen()) {
				try {
					if (DEBUG) {
						LOGGER.info("Removed invalid session ("
								+ (sessionData.isUser() ? sessionData.getUser() : "ANONYMOUS") + " ["
								+ sessionData.getSession().getId() + "])");
					}
					sessionData.getSession().close(CloseStatus.NORMAL);
				} catch (IOException e) {
					LOGGER.warning(
							"Failed to close session (" + sessionData.getSession().getId() + "): " + e.getMessage());
				}
				return true;
			}

			if (this.timeout && !sessionData.isActive()) {
				try {
					if (DEBUG) {
						LOGGER.info("Removed timed out session ("
								+ (sessionData.isUser() ? sessionData.getUser() : "ANONYMOUS") + " ["
								+ sessionData.getSession().getId() + "])");
					}
					sessionData.getSession().close(CloseStatus.NORMAL);
				} catch (IOException e) {
					LOGGER.warning(
							"Failed to close session (" + sessionData.getSession().getId() + "): " + e.getMessage());
				}
				return true;
			}
			return false;
		}), PERIODIC_CHECK_DELAY, PERIODIC_CHECK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		final Authentication auth = (Authentication) session.getAttributes().get("auth");
		SecurityContextHolder.getContext().setAuthentication(auth);

		wsSessions.put(session.getId(), session);

		final WebSocketSessionData sessionData;

		if (isAnonymousContext()) { // anonymous user
			sessionData = new WebSocketSessionData(session.getId(), auth, null, this.beanPath);
		} else { // logged in user
			final UserID user = (UserID) auth.getPrincipal();
			sessionData = new WebSocketSessionData(session.getId(), auth, user, this.beanPath);

			userSessionDatas.put(user.getId(), sessionData);
		}

		wsSessionDatas.put(session.getId(), sessionData);

		onConnect(sessionData);
	}

	private static boolean isAnonymousContext() {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken;
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		if (DEBUG) {
			LOGGER.info("[" + session.getId() + "] Received message: " + message.getPayload());
		}

		final JsonNode incomingJson = objectMapper.readTree(message.getPayload());
		badRequest(!incomingJson.has("destination"), session, "Invalid packet format: missing 'destination' field.",
				message.getPayload());
		String requestPath = incomingJson.get("destination").asText();
		final String packetId = incomingJson.has("packetId") ? incomingJson.get("packetId").asText() : null;
		final JsonNode payload = incomingJson.get("payload");

		final Authentication auth = (Authentication) session.getAttributes().get("auth");
		SecurityContextHolder.getContext().setAuthentication(auth);

		final WSHandlerMethod handlerMethod = resolveMethod(requestPath);
		badRequest(handlerMethod == null, session, "No method found for destination: " + requestPath,
				message.getPayload());
		final Method method = handlerMethod.getMethod();
		badRequest(method == null, session, "No method attached for destination: " + requestPath, message.getPayload());
		final boolean returnsVoid = method.getReturnType().equals(Void.TYPE);
		requestPath = handlerMethod.getInPath();
		badRequest(requestPath == null, session, "No request path defined for destination: " + requestPath,
				message.getPayload());
		final String responsePath = handlerMethod.getOutPath();

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
				badRequest(payload == null, session, "Payload expected for destination: " + requestPath,
						message.getPayload());
				final JavaType javaType = TypeFactory.defaultInstance()
						.constructType(method.getGenericParameterTypes()[1]);
				final Object param = objectMapper.readValue(objectMapper.treeAsTokens(payload), javaType);

				returnValue = method.invoke(bean, userSession, param);
			} else if (method.getParameterCount() == 1) {
				returnValue = method.invoke(bean, userSession);
			} else {
				LOGGER.warning("Method " + method.getName() + " has an invalid number of parameters: "
						+ method.getParameterCount());
				return;
			}

			if (DEBUG) {
				LOGGER.info("Returns void: " + returnsVoid + ", return value: " + returnValue);
			}

			if (!returnsVoid) {
				final ObjectNode root = objectMapper.createObjectNode();
				root.set("payload", objectMapper.valueToTree(returnValue));
				root.put("destination", responsePath);
				if (packetId != null) {
					root.put("packetId", packetId);
				}
				final String jsonResponse = objectMapper.writeValueAsString(root);

				if (DEBUG) {
					LOGGER.info("[" + session.getId() + "] Sending response (" + requestPath + " -> " + responsePath
							+ "): " + jsonResponse);
				}

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

			if (DEBUG) {
				LOGGER.info("[" + session.getId() + "] Error while handling message (" + requestPath + "): "
						+ e.getMessage() + " (" + e.getClass().getName() + ")");
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
		// TODO: reschedule this in WSExtScheduler using a hook
		// cancelScheduledTasks(session);

		final WebSocketSessionData sessionData = wsSessionDatas.get(session.getId());

		wsSessions.remove(session.getId());
		wsSessionDatas.remove(session.getId());

		if (sessionData.isUser())
			userSessionDatas.remove(sessionData.user.getId());

		onDisconnect(sessionData);

		sessionData.invalidate();
	}

	protected void send(WebSocketSession session, String destination, String packetId, Object payload) {
		Objects.requireNonNull(session);
		Objects.requireNonNull(destination);

		if (session.isOpen()) {
			try {
				final String json = buildPacket(destination, packetId, payload);

				if (DEBUG) {
					LOGGER.info("[" + session.getId() + "] Sending message (" + destination + "): " + json);
				}

				session.sendMessage(new TextMessage(json));

				wsSessionDatas.get(session.getId()).lastPacket = System.currentTimeMillis();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			LOGGER.warning("Session is closed: " + session);
		}
	}

	protected void send(WebSocketSessionData sessionData, String destination, String packetId, Object payload) {
		Objects.requireNonNull(sessionData);
		send(sessionData.getSession(), destination, packetId, payload);
	}

	protected void send(WebSocketSessionData sessionData, String destination, Object payload) {
		Objects.requireNonNull(sessionData);
		send(sessionData.getSession(), destination, null, payload);
	}

	protected void send(WebSocketSession session, String destination, Object payload) {
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

		if (DEBUG) {
			LOGGER.info("Broadcasting message to " + count + " sessions: " + json);
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

	public List<WebSocketSessionData> getUserSessions(Set<Long> ids) {
		return ids.stream().map(userSessionDatas::get).filter(Objects::nonNull).toList();
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

	public String getBeanPath() {
		return this.beanPath;
	}

	public class WebSocketSessionData {

		private String id;
		private Authentication auth;
		private final UserID user;
		private long lastPacket = System.currentTimeMillis();
		/** the websocket endpoint path (bean path/http) */
		private final String wsPath;
		/** the current request path (method inPath/destination) */
		private String requestPath;
		private String packetId;

		public WebSocketSessionData(String id, Authentication auth, UserID user, String wsPath) {
			this.id = id;
			this.auth = auth;
			this.user = user;
			this.wsPath = wsPath;
		}

		public void send(String destination, String packetId, Object payload) {
			WSExtServerHandler.this.send(getSession(), destination, packetId, payload);
		}

		public void send(String destination, Object payload) {
			WSExtServerHandler.this.send(getSession(), destination, null, payload);
		}

		public void sendThread(String destination, Object payload) {
			WSExtServerHandler.this.send(getSession(), destination, packetId, payload);
		}

		public void sendThread(Object payload) {
			WSExtServerHandler.this.send(getSession(), requestPath, packetId, payload);
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

		public boolean isActive() {
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

	public class WSHandlerMethod {
		Method method;
		String inPath, outPath;
		boolean allowAnonymous;

		public WSHandlerMethod(Method method, String inPath, String outPath, boolean allowAnonymous) {
			this.method = method;
			this.inPath = inPath;
			this.outPath = outPath;
			this.allowAnonymous = allowAnonymous;
		}

		public Method getMethod() {
			return this.method;
		}

		public String getInPath() {
			return this.inPath;
		}

		public String getOutPath() {
			return this.outPath;
		}

		public boolean isAllowAnonymous() {
			return this.allowAnonymous;
		}

	}

	/* OVERRIDABLE METHODS */
	public void init() {
	}

	public void onConnect(WebSocketSessionData sessionData) {
		for (WSExtComponent comp : components) {
			comp.onConnect(sessionData);
		}
	}

	public void onDisconnect(WebSocketSessionData sessionData) {
		for (WSExtComponent comp : components) {
			comp.onDisconnect(sessionData);
		}
	}

}