package lu.rescue_rush.spring.ws_ext.client;

import static lu.rescue_rush.spring.ws_ext.common.WSPathUtils.normalizeURI;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;

import jakarta.annotation.PostConstruct;
import lu.rescue_rush.spring.ws_ext.client.annotations.WSPersistentConnection;
import lu.rescue_rush.spring.ws_ext.client.components.abstr.ConnectionAwareComponent;
import lu.rescue_rush.spring.ws_ext.client.components.abstr.TransactionAwareComponent;
import lu.rescue_rush.spring.ws_ext.client.components.abstr.WSExtClientComponent;
import lu.rescue_rush.spring.ws_ext.common.MessageData;
import lu.rescue_rush.spring.ws_ext.common.MessageData.TransactionDirection;
import lu.rescue_rush.spring.ws_ext.common.SelfReferencingBean;
import lu.rescue_rush.spring.ws_ext.common.SelfReferencingBeanPostProcessor;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSMapping;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSResponseMapping;

@ComponentScan(basePackageClasses = SelfReferencingBeanPostProcessor.class)
public abstract class WSExtClientHandler extends TextWebSocketHandler implements SelfReferencingBean {

	public static final String GLOBAL_DEBUG_PROPERTY = WSExtClientHandler.class.getSimpleName() + ".isDebug()";
	public static boolean GLOBAL_DEBUG = Boolean.getBoolean(GLOBAL_DEBUG_PROPERTY);

	public final String DEBUG_PROPERTY = this.getClass().getSimpleName() + ".debug";
	public boolean DEBUG = Boolean.getBoolean(DEBUG_PROPERTY);

	private static final Logger STATIC_LOGGER = Logger.getLogger(WSExtClientHandler.class.getName());

	public static final String ERROR_HANDLER_ENDPOINT = "/__error__";

	protected final Logger LOGGER;

	private StandardWebSocketClient client;
	private WebSocketSession session;
	private WebSocketSessionData sessionData;

	private boolean persistentConnection;
	private final String wsPath;
	private WSExtClientHandler bean;
	private final Map<String, WSHandlerMethod> methods;

	private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();

	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	private final Object lockObj = new Object();
	private boolean connectionReady = false;

	private List<WSExtClientComponent> components;
	private List<ConnectionAwareComponent> connectionAwareComponents;
	private List<TransactionAwareComponent> transactionAwareComponents;

	@Autowired
	private ObjectMapper objectMapper;

	public WSExtClientHandler() {
		scan: {
			final Class<?> target = this.getClass();

			final WSMapping beanMapping = target.getAnnotation(WSMapping.class);
			if (beanMapping == null) {
				throw new IllegalStateException("Bean " + target.getName() + " is missing @WSMapping annotation.");
			}

			final String wsPath = beanMapping == null ? "" : beanMapping.path();

			final Map<String, WSHandlerMethod> methods = new ConcurrentHashMap<>();

			for (Method targetMethod : target.getDeclaredMethods()) {
				if (!targetMethod.isAnnotationPresent(WSMapping.class)) {
					continue;
				}

				final WSMapping mapping = targetMethod.getAnnotation(WSMapping.class);
				final WSResponseMapping responseMapping = targetMethod.getAnnotation(WSResponseMapping.class);

				final String inPath = normalizeURI(mapping.path());
				final String outPath = normalizeURI(responseMapping == null ? mapping.path() : responseMapping.path());

				methods.put(mapping.path(), new WSHandlerMethod(targetMethod, inPath, outPath));
			}

			final WSPersistentConnection persistentConnection = target.getAnnotation(WSPersistentConnection.class);
			final boolean persistentConnectionFlag = persistentConnection == null ? false : persistentConnection.value();

			this.wsPath = wsPath.isEmpty() ? null : wsPath;
			this.persistentConnection = persistentConnectionFlag;
			this.methods = methods.entrySet().stream().collect(Collectors.toMap(k -> normalizeURI(k.getKey()), v -> v.getValue()));
			try {
				this.methods
						.putIfAbsent(ERROR_HANDLER_ENDPOINT,
								new WSHandlerMethod(
										WSExtClientHandler.class
												.getDeclaredMethod("handleIncomingError", WebSocketSessionData.class, JsonNode.class),
										ERROR_HANDLER_ENDPOINT, ERROR_HANDLER_ENDPOINT));
			} catch (NoSuchMethodException | SecurityException e) {
				STATIC_LOGGER.warning("Failed to register default error handler: " + e);
				if (GLOBAL_DEBUG) {
					e.printStackTrace();
				}
			}
		}

		LOGGER = Logger.getLogger("WSExtClient # " + this.getClass().getSimpleName() + (wsPath == null ? "" : " (" + wsPath + ")"));

	}

	@PostConstruct
	private void init_() {
		// all the beans have been injected at this point
		scan: {
			final Object bean = this;
			final Class<?> target = this.getClass();

			components = new ArrayList<>();
			connectionAwareComponents = new ArrayList<>();
			transactionAwareComponents = new ArrayList<>();

			for (Field f : target.getDeclaredFields()) {
				if (WSExtClientComponent.class.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					try {
						final WSExtClientComponent comp = (WSExtClientComponent) f.get(bean);
						if (comp != null) {
							attachComponent(comp);
						}
					} catch (IllegalArgumentException | IllegalAccessException e) {
						LOGGER.warning("Failed to access WSExtComponent field " + f.getName() + ": " + e.getMessage());
					}
				}
			}
		}

		init();
	}

	public void start() {
		connect();
	}

	public boolean startRetry(int retries) {
		final boolean persistentConnectionBackup = this.persistentConnection;
		this.persistentConnection = false;

		while (!connectionReady) {
			connect();
			retries--;
			if (retries <= 0) {
				break;
			}
		}

		this.persistentConnection = persistentConnectionBackup;
		return connectionReady;
	}

	public void disconnect() {
		this.persistentConnection = false;

		LOGGER.info("Disconnecting WebSocket client...");

		synchronized (session) {
			if (session != null && session.isOpen()) {
				try {
					session.close();
				} catch (IOException e) {
					if (GLOBAL_DEBUG) {
						LOGGER.warning("Error while closing WebSocket session: " + e.getMessage());
					}
				}
			}
		}

		LOGGER.info("Disconnected WebSocket client !");
	}

	public void scheduleStart() {
		reconnectScheduler.submit(this::connect);
	}

	private void connect() {
		if (session != null) {
			synchronized (session) {
				if (session != null || session.isOpen()) {
					return;
				}
			}
		}

		final URI path = wsPath == null ? bean.buildRemoteURI() : bean.buildRemoteURI().resolve(wsPath);

		try {
			client = new StandardWebSocketClient();
			session = client.execute(this, bean.buildHttpHeaders(), path).get();
		} catch (Exception e) {
			if (GLOBAL_DEBUG) {
				LOGGER.log(Level.WARNING, "Connection failed to '" + path + "': ", e);
			} else {
				LOGGER.log(Level.WARNING, "Connection failed to '" + path + "' (" + e.getMessage() + ")");
			}
			scheduleReconnect();
		}
	}

	private void scheduleReconnect() {
		if (persistentConnection) {
			if (GLOBAL_DEBUG) {
				LOGGER.info("Reconnecting in 5 sec...");
			}
			reconnectScheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
		}
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		setSession(session);
		sessionData = new WebSocketSessionData();
		ready();

		bean.onConnect(sessionData);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		setSession(session);

		block();
		bean.onDisconnect(sessionData);

		scheduleReconnect();
		this.sessionData = null;
		this.session = null;
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		setSession(session);

		if (GLOBAL_DEBUG) {
			LOGGER.info("Received message: " + message.getPayload());
		}

		if (sessionData == null || session == null || !session.isOpen() || !connectionReady) {
			LOGGER.warning("Received message after the session was closed.");
			return;
		}

		final JsonNode incomingJson = objectMapper.readTree(message.getPayload());
		badRequest(!incomingJson.has("destination"), "Invalid packet format: missing 'destination' field.", message.getPayload());
		String requestPath = incomingJson.get("destination").asText();
		final String packetId = incomingJson.has("packetId") ? incomingJson.get("packetId").asText() : null;
		final JsonNode payload = incomingJson.get("payload");

		final WSHandlerMethod handlerMethod = resolveMethod(requestPath);
		badRequest(handlerMethod == null, "No method found for destination: " + requestPath, message.getPayload());
		final Method method = handlerMethod.getMethod();
		badRequest(method == null, "No method attached for destination: " + requestPath, message.getPayload());
		final boolean returnsVoid = method.getReturnType().equals(Void.TYPE);
		requestPath = handlerMethod.getInPath();
		badRequest(requestPath == null, "No request path defined for destination: " + requestPath, message.getPayload());
		final String responsePath = handlerMethod.getOutPath();

		sessionData.setRequestPath(requestPath);
		sessionData.setPacketId(packetId);
		sessionData.lastPacket = System.currentTimeMillis();

		Exception err = null;
		try {
			Object returnValue = null, payloadObj = null;

			if (method.getParameterCount() == 2) {
				badRequest(payload == null, "Payload expected for destination: " + requestPath, message.getPayload());
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

			if (!returnsVoid) {
				final ObjectNode root = objectMapper.createObjectNode();
				root.set("payload", objectMapper.valueToTree(returnValue));
				root.put("destination", responsePath);
				if (packetId != null) {
					root.put("packetId", packetId);
				}
				final String jsonResponse = objectMapper.writeValueAsString(root);

				if (GLOBAL_DEBUG) {
					LOGGER.info("Sending response (" + requestPath + " -> " + responsePath + "): " + jsonResponse);
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
					LOGGER.warning("Failed to notify ConnectionAwareComponent '" + comp.getClass().getName() + "': " + e.getMessage());
					if (GLOBAL_DEBUG) {
						e.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			err = e;

			final ObjectNode root = objectMapper.createObjectNode();
			root.put("status", 500);
			root.put("destination", ERROR_HANDLER_ENDPOINT);
			root.set("packet", incomingJson);

			if (e instanceof ResponseStatusException rse) {
				root.put("status", rse.getStatusCode().value());
				root.put("message", rse.getReason());
			}

			session.sendMessage(new TextMessage(root.toString()));
		}

		if (err != null) {
			throw new RuntimeException("Exception caught while handling packet for '" + requestPath + "': " + incomingJson.toPrettyString(),
					err);
		}
	}

	protected void handleIncomingError(WebSocketSessionData sessionData, JsonNode packet) {
		if (GLOBAL_DEBUG) {
			LOGGER.warning("[" + sessionData.getSession().getId() + "] Error packet received: " + packet.toString());
		}
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		setSession(session);

		if (GLOBAL_DEBUG) {
			LOGGER.warning("Transport error on session: " + exception.getMessage());
		}

		if (exception instanceof ResponseStatusException rse) {
			LOGGER.warning("Response status error: " + rse.getReason());
		} else {
			LOGGER.log(Level.WARNING, "Unexpected transport error: ", exception);
		}

		if (persistentConnection) {
			scheduleReconnect();
		}
	}

	private void badRequest(boolean b, String msg, String content) {
		if (!b)
			return;
		if (GLOBAL_DEBUG)
			LOGGER.warning(msg + " (" + content + ")");
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
	}

	public void send(String destination, String packetId, Object payload) {
		Objects.requireNonNull(destination);

		if (session == null || !session.isOpen()) {
			throw new IllegalStateException("Session is closed.");
		}

		try {
			final String json = buildPacket(destination, packetId, payload);

			if (GLOBAL_DEBUG) {
				LOGGER.info("Sending packet (" + destination + "): " + json);
			}

			session.sendMessage(new TextMessage(json));

			sessionData.lastPacket = System.currentTimeMillis();

			for (TransactionAwareComponent m : transactionAwareComponents) {
				try {
					m.onTransaction(sessionData, TransactionDirection.OUT, null, new MessageData(destination, packetId, null));
				} catch (Exception e) {
					LOGGER.warning("Failed to notify TransactionAwareComponent '" + m.getClass().getName() + "': " + e.getMessage());
					if (GLOBAL_DEBUG) {
						e.printStackTrace();
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Exception caught while sending packet.", e);
		}
	}

	public void send(String destination, Object payload) {
		send(destination, null, payload);
	}

	public void send(String destination) {
		send(destination, null, null);
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
		if (matchingPatterns.isEmpty())
			return null;

		final String bestPattern = matchingPatterns.get(0);
		final WSHandlerMethod bestMatch = methods.get(bestPattern);

		return bestMatch;
	}

	protected void setSession(WebSocketSession session) {
		if (this.session != session && GLOBAL_DEBUG) {
			LOGGER.info("WebSocketSession instance changed for some reason.");
		}
		if (session == null) {
			LOGGER.info("WebSocketSession instance closed.");
		}
		this.session = session;
	}

	public boolean awaitConnection() {
		try {
			synchronized (lockObj) {
				while (!connectionReady) {
					lockObj.wait();
				}
			}
			return true;
		} catch (InterruptedException e) {
			if (GLOBAL_DEBUG) {
				LOGGER.warning("Thread: " + Thread.currentThread().getName() + " interrupted while awaiting connection");
			}
			Thread.interrupted();
			return false;
		}
	}

	protected void ready() {
		synchronized (lockObj) {
			connectionReady = true;
			lockObj.notifyAll();
		}
	}

	protected void block() {
		synchronized (lockObj) {
			connectionReady = false;
		}
	}

	public String getWsPath() {
		return this.wsPath;
	}

	public boolean isPersistentConnection() {
		return this.persistentConnection;
	}

	@Override
	public void setProxy(Object bean) {
		this.bean = (WSExtClientHandler) bean;
	}

	public void attachComponent(WSExtClientComponent comp) {
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

	public boolean hasComponentsOfType(Class<? extends WSExtClientComponent> clazz) {
		return components.stream().anyMatch(c -> clazz.isInstance(c));
	}

	public <T extends WSExtClientComponent> Optional<T> getComponentOfType(Class<T> clazz) {
		return components.stream().filter(c -> clazz.isInstance(c)).map(c -> clazz.cast(c)).findFirst();
	}

	public class WebSocketSessionData {

		private long lastPacket = System.currentTimeMillis();
		private String requestPath, packetId;

		public WebSocketSession getSession() {
			return session;
		}

		public long getLastPacket() {
			return lastPacket;
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

		public void invalidate() {
			clearContext();
		}

		public boolean hasPacketId() {
			return packetId != null;
		}

		public void clearContext() {
			this.requestPath = null;
			this.packetId = null;
		}

	}

	public class WSHandlerMethod {
		Method method;
		String inPath, outPath;

		public WSHandlerMethod(Method method, String inPath, String outPath) {
			this.method = method;
			this.inPath = inPath;
			this.outPath = outPath;
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

	}

	public abstract WebSocketHttpHeaders buildHttpHeaders();

	public abstract URI buildRemoteURI();

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
				if (GLOBAL_DEBUG) {
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
				if (GLOBAL_DEBUG) {
					e.printStackTrace();
				}
			}
		}
	}

}