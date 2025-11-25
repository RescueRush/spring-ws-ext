package lu.rescue_rush.spring.ws_ext.server;

import static lu.rescue_rush.spring.ws_ext.common.WSPathUtils.normalizeURI;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
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
import lu.rescue_rush.spring.ws_ext.common.MessageData;
import lu.rescue_rush.spring.ws_ext.common.MessageData.TransactionDirection;
import lu.rescue_rush.spring.ws_ext.common.SelfReferencingBean;
import lu.rescue_rush.spring.ws_ext.common.SelfReferencingBeanPostProcessor;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSMapping;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSResponseMapping;
import lu.rescue_rush.spring.ws_ext.server.abstr.WSConnectionController;
import lu.rescue_rush.spring.ws_ext.server.abstr.WSTransactionController;
import lu.rescue_rush.spring.ws_ext.server.annotation.AllowAnonymous;
import lu.rescue_rush.spring.ws_ext.server.annotation.IgnoreNull;
import lu.rescue_rush.spring.ws_ext.server.annotation.WSTimeout;
import lu.rescue_rush.spring.ws_ext.server.component.abstr.ConnectionAwareComponent;
import lu.rescue_rush.spring.ws_ext.server.component.abstr.TransactionAwareComponent;
import lu.rescue_rush.spring.ws_ext.server.component.abstr.WSExtServerComponent;
import lu.rescue_rush.spring.ws_ext.server.config.QuietExceptionWebSocketHandlerDecorator;
import lu.rescue_rush.spring.ws_ext.server.config.SimpleHandshakeInterceptor;

@ComponentScan(basePackageClasses = SelfReferencingBeanPostProcessor.class)
public class WSExtServerHandler extends TextWebSocketHandler implements SelfReferencingBean {

	public static final String DEBUG_PROPERTY = WSExtServerHandler.class.getSimpleName() + ".debug";
	public boolean DEBUG = Boolean.getBoolean(DEBUG_PROPERTY);

	public static final long TIMEOUT = 60_000;
	public static final long PERIODIC_CHECK_DELAY = 10_000;
	public static final String ERROR_HANDLER_ENDPOINT = "/__error__";

	private static final Logger STATIC_LOGGER = Logger.getLogger(WSExtServerHandler.class.getName());

	protected final Logger LOGGER;

	private WSExtServerHandler bean;
	protected final String beanPath;
	protected final Map<String, WSHandlerMethod> methods;

	/** custom session data id, session */
	private final Map<String, WebSocketSession> wsSessions = new ConcurrentHashMap<>();
	/** session id, session data */
	private final Map<String, WebSocketSessionData> wsSessionDatas = new ConcurrentHashMap<>();

	protected final AntPathMatcher pathMatcher = new AntPathMatcher();

	protected final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

	protected List<WSExtServerComponent> components;
	protected List<ConnectionAwareComponent> connectionAwareComponents;
	protected List<TransactionAwareComponent> transactionAwareComponents;

	protected final boolean allowAnonymous;
	protected final boolean timeout;
	protected final long timeoutDelay;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired(required = false)
	private WSConnectionController connectionController;
	@Autowired(required = false)
	private WSTransactionController transactionController;

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
			this.allowAnonymous = target.isAnnotationPresent(AllowAnonymous.class);

			final Map<String, WSHandlerMethod> methods = new ConcurrentHashMap<>();

			for (Method targetMethod : target.getDeclaredMethods()) {
				if (!targetMethod.isAnnotationPresent(WSMapping.class)) {
					continue;
				}

				final WSMapping mapping = targetMethod.getAnnotation(WSMapping.class);
				final WSResponseMapping responseMapping = targetMethod.getAnnotation(WSResponseMapping.class);
				final AllowAnonymous allowAnonymous = targetMethod.getAnnotation(AllowAnonymous.class);
				final IgnoreNull ignoreNull = targetMethod.getAnnotation(IgnoreNull.class);

				final String inPath = normalizeURI(mapping.path());
				final String outPath = normalizeURI(responseMapping == null ? mapping.path() : responseMapping.path());
				final boolean allowAnonymousFlag = allowAnonymous != null;
				final boolean ignoreNullFlag = ignoreNull != null;

				methods.put(mapping.path(), new WSHandlerMethod(targetMethod, inPath, outPath, allowAnonymousFlag, ignoreNullFlag));
			}

			this.beanPath = beanMapping.path();
			this.methods = methods.entrySet().stream().collect(Collectors.toMap(k -> normalizeURI(k.getKey()), v -> v.getValue()));
			try {
				this.methods
						.putIfAbsent(ERROR_HANDLER_ENDPOINT,
								new WSHandlerMethod(
										WSExtServerHandler.class
												.getDeclaredMethod("handleIncomingError", WebSocketSessionData.class, JsonNode.class),
										ERROR_HANDLER_ENDPOINT, ERROR_HANDLER_ENDPOINT, true, true));
			} catch (NoSuchMethodException | SecurityException e) {
				STATIC_LOGGER.warning("Failed to register default error handler: " + e);
				if (DEBUG) {
					e.printStackTrace();
				}
			}
			this.timeout = timeout != null ? timeout.value() : true;
			this.timeoutDelay = timeout != null ? timeout.timeout() : WSExtServerHandler.TIMEOUT;
		}

		this.LOGGER = Logger.getLogger("WSExtServer # " + beanPath);

		schedulePeriodicCleanup();
	}

	@PostConstruct
	protected void init_() {
		// all the beans have been injected at this point
		final Object bean = this;
		final Class<?> target = this.getClass();

		components = new ArrayList<>();
		connectionAwareComponents = new ArrayList<>();
		transactionAwareComponents = new ArrayList<>();

		for (Field f : target.getDeclaredFields()) {
			if (WSExtServerComponent.class.isAssignableFrom(f.getType())) {
				f.setAccessible(true);
				try {
					final WSExtServerComponent comp = (WSExtServerComponent) f.get(bean);
					if (comp != null) {
						attachComponent(comp);
					}
				} catch (IllegalArgumentException | IllegalAccessException e) {
					LOGGER.warning("Failed to access WSExtComponent field " + f.getName() + ": " + e.getMessage());
				}
			}
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
						LOGGER.info("Removed invalid session [" + sessionData.getSession().getId() + "])");
					}
					sessionData.getSession().close(CloseStatus.NORMAL);
				} catch (IOException e) {
					LOGGER.warning("Failed to close session (" + sessionData.getSession().getId() + "): " + e.getMessage());
				}
				return true;
			}

			if (this.timeout && !sessionData.isActive()) {
				try {
					if (DEBUG) {
						LOGGER.info("Removed timed out session [" + sessionData.getSession().getId() + "])");
					}
					sessionData.getSession().close(CloseStatus.NORMAL);
				} catch (IOException e) {
					LOGGER.warning("Failed to close session (" + sessionData.getSession().getId() + "): " + e.getMessage());
				}
				return true;
			}
			return false;
		}), PERIODIC_CHECK_DELAY, PERIODIC_CHECK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		final WebSocketSessionData sessionData = new WebSocketSessionData(
				(String) session.getAttributes().getOrDefault(SimpleHandshakeInterceptor.HTTP_ATTRIBUTE_ID, session.getId()));
		wsSessions.put(sessionData.getId(), session);
		wsSessionDatas.put(session.getId(), sessionData);

		try {
			if (transactionController != null && !transactionController.beforeTransaction(sessionData, Optional.empty())) {
				sessionData.close(CloseStatus.NOT_ACCEPTABLE);
			}

			if (connectionController != null && !connectionController.beforeConnection(sessionData)) {
				sessionData.close(CloseStatus.NOT_ACCEPTABLE);
			}

			onConnect(sessionData);
		} catch (IOException e) {
			throw new IllegalArgumentException("Couldn't close session.", e);
		} finally {
			if (transactionController != null) {
				transactionController.afterTransaction(sessionData);
			}
		}
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		synchronized (session) {
			final WebSocketSessionData sessionData = wsSessionDatas.get(session.getId());

			if (sessionData == null) {
				LOGGER
						.info("[" + session.getId() + "] Session closed because the associated data wasn't found. Received message: "
								+ message.getPayload());
				session.close(CloseStatus.SERVER_ERROR);
				return;
			}

			if (DEBUG) {
				LOGGER.info("[" + sessionData.getId() + "] Received message: " + message.getPayload());
			}

			final JsonNode incomingJson = objectMapper.readTree(message.getPayload());

			sessionData.lastPacket = System.currentTimeMillis();

			Exception err = null;

			try {
				badRequest(!incomingJson.has("destination"),
						sessionData,
						"Invalid packet format: missing 'destination' field.",
						message.getPayload());
				String requestPath = incomingJson.get("destination").asText();
				sessionData.setRequestPath(requestPath);

				final String packetId = incomingJson.has("packetId") ? incomingJson.get("packetId").asText() : null;
				sessionData.setPacketId(packetId);

				final JsonNode payload = incomingJson.get("payload");

				final WSHandlerMethod handlerMethod = resolveMethod(requestPath);
				badRequest(handlerMethod == null, sessionData, "No method found for destination: " + requestPath, message.getPayload());
				final Method method = handlerMethod.getMethod();
				badRequest(method == null, sessionData, "No method attached for destination: " + requestPath, message.getPayload());
				final boolean returnsVoid = method.getReturnType().equals(Void.TYPE);
				requestPath = handlerMethod.getInPath();
				badRequest(requestPath == null,
						sessionData,
						"No request path defined for destination: " + requestPath,
						message.getPayload());
				final String responsePath = handlerMethod.getOutPath();
				sessionData.setResponsePath(responsePath);

				if (transactionController != null && !transactionController.beforeTransaction(sessionData, Optional.of(handlerMethod))) {
					throw new ResponseStatusException(HttpStatus.FORBIDDEN);
				}

				Object returnValue = null, payloadObj = null;

				if (method.getParameterCount() == 2) {
					badRequest(payload == null, sessionData, "Payload expected for destination: " + requestPath, message.getPayload());
					final JavaType javaType = TypeFactory.defaultInstance().constructType(method.getGenericParameterTypes()[1]);
					if (javaType.isTypeOrSuperTypeOf(JsonNode.class)) {
						payloadObj = payload;
					} else {
						payloadObj = objectMapper.readValue(objectMapper.treeAsTokens(payload), javaType);
					}

					returnValue = method.invoke(bean, sessionData, payloadObj);
				} else if (method.getParameterCount() == 1) {
					returnValue = method.invoke(bean, sessionData);
				} else {
					LOGGER.warning("Method " + method.getName() + " has an invalid number of parameters: " + method.getParameterCount());
					return;
				}

				// always send response except when it returns void or
				// it's null and it should ignore null returns
				final boolean sendResponse = !(returnsVoid || (returnValue == null && handlerMethod.isIgnoreNull()));

				if (sendResponse) {
					final String jsonResponse = buildPacket(responsePath, packetId, returnValue);

					if (DEBUG) {
						LOGGER
								.info("[" + sessionData.getId() + "] Sending response (" + requestPath + " -> " + responsePath + "): "
										+ jsonResponse);
					}

					session.sendMessage(new TextMessage(jsonResponse));
				}

				for (TransactionAwareComponent comp : transactionAwareComponents) {
					try {
						comp
								.onTransaction(sessionData,
										returnsVoid ? TransactionDirection.IN : TransactionDirection.IN_OUT,
										new MessageData(requestPath, packetId, payloadObj),
										returnsVoid ? null : new MessageData(responsePath, packetId, returnValue));
					} catch (Exception e) {
						LOGGER
								.warning("[" + sessionData.getId() + "] Failed to notify ConnectionAwareComponent '"
										+ comp.getClass().getName() + "': " + e.getMessage());
						if (DEBUG) {
							e.printStackTrace();
						}
					}
				}
			} catch (Exception e) {
				err = e;

				sendError(sessionData, e, incomingJson);
			} finally {
				if (transactionController != null) {
					transactionController.afterTransaction(sessionData);
				}

				sessionData.clearContext();
			}
		}
	}

	protected void sendError(WebSocketSessionData sessionData, Exception e, JsonNode incomingJson) {
		final ObjectNode payload = objectMapper.createObjectNode();

		payload.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());

		if (e instanceof ResponseStatusException rse) {
			payload.put("status", rse.getStatusCode().value());
			payload.put("message", rse.getReason());
		}

		payload.set("incoming", incomingJson);

		if (handleMessageError(sessionData, incomingJson, e)) {
			sessionData.sendThread(ERROR_HANDLER_ENDPOINT, payload);
		}
	}

	/**
	 * To override: handles the caught exception when handling an {@link #handleTextMessage() incoming
	 * packet}.<br>
	 * By default: logs the error and prints the stack trace if in {@link #DEBUG} mode.<br>
	 * Throwing any exception will pass it to the {@link QuietExceptionWebSocketHandlerDecorator}, which
	 * will close the session without forwarding the exception. It is recommended to handle errors using
	 * {@link WebSocketSessionData#close} if needed.
	 * 
	 * @return weather the exception will be forwarded to the client (default: true)
	 */
	protected boolean handleMessageError(WebSocketSessionData sessionData, JsonNode incomingJson, Throwable e) {
		if (DEBUG) {
			LOGGER
					.severe("[" + sessionData.getId() + "] Error while handling message (" + sessionData.getRequestPath() + "): "
							+ e.getMessage() + " (" + e.getClass().getName() + ")");
			e.printStackTrace();
		} else {
			LOGGER.severe("[" + sessionData.getId() + "] Error when handling incoming packet: " + e);
			while (e.getCause() != null) {
				e = e.getCause();
				LOGGER.severe("Caused by:" + e);
			}
		}
		return true;
	}

	/**
	 * To override incoming {@link #ERROR_HANDLER_ENDPOINT} handler
	 */
	protected void handleIncomingError(WebSocketSessionData sessionData, JsonNode packet) {
		if (DEBUG) {
			LOGGER.warning("[" + sessionData.getId() + "] Error packet received: " + packet.toString());
		}
	}

	protected void badRequest(boolean b, WebSocketSessionData sessionData, String msg, String content) {
		if (!b) {
			return;
		}
		if (DEBUG) {
			LOGGER.warning("[" + sessionData.getId() + "] " + msg + " (" + content + ")");
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		final WebSocketSessionData sessionData = wsSessionDatas.get(session.getId());

		try {
			if (transactionController != null) {
				transactionController.beforeTransaction(sessionData, Optional.empty());
			}

			onDisconnect(sessionData);

			if (connectionController != null) {
				connectionController.afterConnection(sessionData);
			}
		} finally {
			sessionData.invalidate();

			if (transactionController != null) {
				transactionController.afterTransaction(sessionData);
			}

			wsSessions.remove(session.getId());
			wsSessionDatas.remove(session.getId());
		}
	}

	private boolean send(WebSocketSessionData sessionData, String destination, String packetId, Object payload) {
		Objects.requireNonNull(sessionData);
		Objects.requireNonNull(destination);

		if (!sessionData.isValid()) {
			return false;
		}

		final WebSocketSession session = sessionData.getSession();

		if (session == null) {
			return false;
		}

		synchronized (session) {
			if (session.isOpen()) {
				try {
					final String json = buildPacket(destination, packetId, payload);

					if (DEBUG) {
						LOGGER.info("[" + sessionData.getId() + "] Sending message (" + destination + "): " + json);
					}

					if (!session.isOpen()) {
						LOGGER.warning("Session is closed: " + session);
					}

					session.sendMessage(new TextMessage(json));

					wsSessionDatas.get(session.getId()).lastPacket = System.currentTimeMillis();

					for (TransactionAwareComponent m : transactionAwareComponents) {
						try {
							m.onTransaction(sessionData, TransactionDirection.OUT, null, new MessageData(destination, packetId, null));
						} catch (Exception e) {
							LOGGER
									.warning("[" + sessionData.getId() + "] Failed to notify TransactionAwareComponent '"
											+ m.getClass().getName() + "': " + e.getMessage());
							if (DEBUG) {
								e.printStackTrace();
							}
						}
					}

					return true;
				} catch (IOException e) {
					LOGGER.warning("[" + sessionData.getId() + "] Failed to send message to session: " + e);
					if (DEBUG) {
						e.printStackTrace();
					}
				}
			} else {
				LOGGER.warning("[" + sessionData.getId() + "] Session is closed.");
			}
		}

		return false;
	}

	public int broadcast(Predicate<WebSocketSessionData> predicate, String destination, Object payload) {
		int count = 0;

		final String json = buildPacket(destination, null, payload);
		final TextMessage msg = new TextMessage(json);

		for (WebSocketSessionData wsSessionData : wsSessionDatas.values()) {
			if (!wsSessionData.isValid() || !predicate.test(wsSessionData)) {
				continue;
			}

			final WebSocketSession wsSession = wsSessionData.getSession();
			synchronized (wsSession) {
				if (wsSession.isOpen()) {
					try {
						wsSession.sendMessage(msg);
					} catch (IOException e) {
						LOGGER.warning("[" + wsSessionData.getId() + "] Failed to send message to session: " + e.getMessage());
						if (DEBUG) {
							e.printStackTrace();
						}
					}
					count++;
				} else {
					LOGGER.warning("[" + wsSessionData.getId() + "] Session is closed.");
				}
			}
		}

		if (DEBUG) {
			LOGGER.info("Broadcasting message to " + count + " sessions on " + destination + ": " + json);
		}

		return count;
	}

	public int broadcast(Predicate<WebSocketSessionData> predicate, String destination, Function<WebSocketSessionData, Object> payload) {
		int count = 0;

		for (WebSocketSessionData wsSessionData : wsSessionDatas.values()) {
			if (!wsSessionData.isValid() || !predicate.test(wsSessionData)) {
				continue;
			}

			final WebSocketSession wsSession = wsSessionData.getSession();
			synchronized (wsSession) {
				if (wsSession.isOpen()) {
					try {
						final String json = buildPacket(destination, null, payload.apply(wsSessionData));
						wsSession.sendMessage(new TextMessage(json));
					} catch (IOException e) {
						LOGGER.warning("[" + wsSessionData.getId() + "] Failed to send message to session: " + e.getMessage());
						if (DEBUG) {
							e.printStackTrace();
						}
					}
					count++;
				} else {
					LOGGER.warning("[" + wsSessionData.getId() + "] Session is closed.");
				}
			}
		}

		if (DEBUG) {
			LOGGER.info("Broadcasting message to " + count + " sessions on " + destination);
		}

		return count;
	}

	public int broadcast(Collection<String> ids, String destination, Object payload) {
		int count = 0;

		final String json = buildPacket(destination, null, payload);
		final TextMessage msg = new TextMessage(json);

		for (String id : ids) {
			if (!hasConnectedSessionData(id)) {
				continue;
			}
			final WebSocketSessionData wsSessionData = getConnectedSessionData(id);
			if (wsSessionData == null || !wsSessionData.isValid()) {
				continue;
			}

			final WebSocketSession wsSession = wsSessionData.getSession();
			synchronized (wsSession) {
				if (wsSession.isOpen()) {
					try {
						wsSession.sendMessage(msg);
					} catch (IOException e) {
						LOGGER.warning("[" + wsSessionData.getId() + "] Failed to send message to session: " + e.getMessage());
						if (DEBUG) {
							e.printStackTrace();
						}
					}
					count++;
				} else {
					LOGGER.warning("[" + wsSessionData.getId() + "] Session is closed.");
				}
			}
		}

		if (DEBUG) {
			LOGGER.info("Broadcasting message to " + count + " sessions on " + destination + ": " + json);
		}

		return count;
	}

	public int broadcast(Collection<String> ids, String destination, Function<WebSocketSessionData, Object> payload) {
		int count = 0;

		for (String id : ids) {
			if (!hasConnectedSessionData(id)) {
				continue;
			}
			final WebSocketSessionData wsSessionData = getConnectedSessionData(id);
			if (wsSessionData == null || !wsSessionData.isValid()) {
				continue;
			}

			final WebSocketSession wsSession = wsSessionData.getSession();
			synchronized (wsSession) {
				if (wsSession.isOpen()) {
					try {
						final String json = buildPacket(destination, null, payload.apply(wsSessionData));
						wsSession.sendMessage(new TextMessage(json));
					} catch (IOException e) {
						LOGGER.warning("[" + wsSessionData.getId() + "] Failed to send message to session: " + e.getMessage());
						if (DEBUG) {
							e.printStackTrace();
						}
					}
					count++;
				} else {
					LOGGER.warning("[" + wsSessionData.getId() + "] Session is closed.");
				}
			}
		}

		if (DEBUG) {
			LOGGER.info("Broadcasting message to " + count + " sessions on " + destination);
		}

		return count;
	}

	public int broadcast(String destination, Object payload) {
		return broadcast((s) -> true, destination, payload);
	}

	protected String buildPacket(String destination, String packetId, Object payload) {
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
		if (matchingPatterns.isEmpty()) {
			return null;
		}

		final String bestPattern = matchingPatterns.get(0);
		final WSHandlerMethod bestMatch = methods.get(bestPattern);

		return bestMatch;
	}

	public void attachComponent(WSExtServerComponent comp) {
		components.add(comp);

		if (comp instanceof ConnectionAwareComponent cac) {
			connectionAwareComponents.add(cac);
		}
		if (comp instanceof TransactionAwareComponent mac) {
			transactionAwareComponents.add(mac);
		}

		comp.setHandlerBean(this);
		comp.init();
	}

	public Collection<WebSocketSession> getConnectedSessions() {
		return wsSessions.values();
	}

	public Collection<WebSocketSessionData> getConnectedSessionDatas() {
		return wsSessionDatas.values();
	}

	public boolean hasConnectedSessionData(String id) {
		return wsSessions.containsKey(id);
	}

	public WebSocketSessionData getConnectedSessionData(String id) {
		if (!wsSessions.containsKey(id)) {
			return null;
		}
		return wsSessionDatas.get(wsSessions.get(id).getId());
	}

	public int getConnectedSessionCount() {
		return wsSessions.size();
	}

	public String getBeanPath() {
		return this.beanPath;
	}

	public WSExtServerHandler getBean() {
		return bean;
	}

	public boolean isAllowAnonymous() {
		return allowAnonymous;
	}

	public boolean isTimeout() {
		return timeout;
	}

	public long getTimeoutDelay() {
		return timeoutDelay;
	}

	@Override
	public void setProxy(Object bean) {
		this.bean = (WSExtServerHandler) bean;
	}

	public boolean hasComponentsOfType(Class<? extends WSExtServerComponent> clazz) {
		return components.stream().anyMatch(c -> clazz.isInstance(c));
	}

	public <T extends WSExtServerComponent> Optional<T> getComponentOfType(Class<T> clazz) {
		return components.stream().filter(c -> clazz.isInstance(c)).map(c -> clazz.cast(c)).findFirst();
	}

	public class WebSocketSessionData {

		private boolean valid = true;

		private final String id;
		// private final UserID user;
		private long lastPacket = System.currentTimeMillis();
		/** the current request path (method inPath/destination) */
		private String requestPath;
		private String responsePath;
		private String packetId;

		public WebSocketSessionData(String id) {
			this.id = id;
		}

		public void send(String destination, String packetId, Object payload) {
			WSExtServerHandler.this.send(this, destination, packetId, payload);
		}

		public void send(String destination, Object payload) {
			WSExtServerHandler.this.send(this, destination, null, payload);
		}

		/**
		 * Preserves the packet id
		 */
		public void sendThread(String destination, Object payload) {
			WSExtServerHandler.this.send(this, destination, packetId, payload);
		}

		/**
		 * Send the packet to the response path
		 */
		public void sendAnswer(Object payload) {
			WSExtServerHandler.this.send(this, responsePath, null, payload);
		}

		/**
		 * Send the packet to the response path
		 */
		public void sendAnswerThread(String packetId, Object payload) {
			WSExtServerHandler.this.send(this, responsePath, packetId, payload);
		}

		/**
		 * Preserves the packet id and sends the packet to the response path
		 */
		public void sendAnswerThread(Object payload) {
			WSExtServerHandler.this.send(this, responsePath, packetId, payload);
		}

		/**
		 * this has to get called at the end, only by the WSExtServerHandler and no other component(s)
		 */
		void invalidate() {
			clearContext();

			valid = false;
		}

		public String getId() {
			return id;
		}

		public WebSocketSession getSession() {
			if (id == null) { // session invalidated
				throw new IllegalStateException("This session is invalid and should be used further.");
			}
			return getSessionInternal();
		}

		WebSocketSession getSessionInternal() {
			return wsSessions.get(this.id);
		}

		public boolean isValid() {
			return valid;
		}

		public boolean isOpen() {
			final WebSocketSession session = getSessionInternal();
			return isValid() && session != null && session.isOpen();
		}

		public long getLastPing() {
			return lastPacket;
		}

		public boolean isActive() {
			return (System.currentTimeMillis() - lastPacket) < timeoutDelay;
		}

		public WSExtServerHandler getWsHandlerBean() {
			return bean;
		}

		public String getRequestPath() {
			return requestPath;
		}

		public void setRequestPath(String requestPath) {
			this.requestPath = requestPath;
		}

		public String getResponsePath() {
			return responsePath;
		}

		public void setResponsePath(String responsePath) {
			this.responsePath = responsePath;
		}

		public String getPacketId() {
			return packetId;
		}

		public void setPacketId(String packetId) {
			this.packetId = packetId;
		}

		public boolean hasRequestPath() {
			return requestPath != null;
		}

		public boolean hasPacketId() {
			return packetId != null;
		}

		void clearContext() {
			this.requestPath = null;
			this.packetId = null;
		}

		public void close(CloseStatus closeStatus) throws IOException {
			if (!isValid()) {
				checkStatus();
				return;
			}

			final WebSocketSession session = getSession();

			synchronized (session) {
				session.close(closeStatus);
			}
		}

		public void close() throws IOException {
			close(CloseStatus.NORMAL);
		}

		/**
		 * Checks if the session is valid (not invalidated and opened) and if it's still correctly
		 * registered. Else closes the connection with the code {@link CloseStatus.SERVER_ERROR}.
		 */
		public void checkStatus() throws IOException {
			if (isValid() && wsSessionDatas.containsKey(this.id) && wsSessions.containsKey(this.id)) {
				return;
			} else {
				close(CloseStatus.SERVER_ERROR);
			}
		}

	}

	public class WSHandlerMethod {
		private final Method method;

		private final String inPath;
		private final String outPath;

		private final boolean allowAnonymous;
		private final boolean ignoreNull;

		public WSHandlerMethod(Method method, String inPath, String outPath, boolean allowAnonymous, boolean ignoreNull) {
			this.method = method;
			this.inPath = inPath;
			this.outPath = outPath;
			this.allowAnonymous = allowAnonymous;
			this.ignoreNull = ignoreNull;
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

		public boolean isIgnoreNull() {
			return ignoreNull;
		}

		@Override
		public String toString() {
			return "WSHandlerMethod [method=" + method + ", inPath=" + inPath + ", outPath=" + outPath + ", allowAnonymous="
					+ allowAnonymous + ", ignoreNull=" + ignoreNull + "]";
		}

	}

	/* OVERRIDABLE METHODS */
	public void init() {
	}

	public void onConnect(WebSocketSessionData sessionData) {
		if (connectionAwareComponents == null)
			return;
		for (ConnectionAwareComponent comp : connectionAwareComponents) {
			try {
				comp.onConnect(sessionData);
			} catch (Exception e) {
				LOGGER.warning("Failed to notify ConnectionAwareComponent '" + comp.getClass().getName() + "': " + e.getMessage());
				if (DEBUG) {
					e.printStackTrace();
				}
			}
		}
	}

	public void onDisconnect(WebSocketSessionData sessionData) {
		if (connectionAwareComponents == null)
			return;
		for (ConnectionAwareComponent comp : connectionAwareComponents) {
			try {
				comp.onDisconnect(sessionData);
			} catch (Exception e) {
				LOGGER.warning("Failed to notify ConnectionAwareComponent '" + comp.getClass().getName() + "': " + e.getMessage());
				if (DEBUG) {
					e.printStackTrace();
				}
			}
		}
	}

}