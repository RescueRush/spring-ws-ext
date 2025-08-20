package lu.rescue_rush.spring.ws_ext.client;

import static lu.rescue_rush.spring.ws_ext.common.WSPathUtils.normalizeURI;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
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
import lu.rescue_rush.spring.ws_ext.client.WSExtClientMappingRegistry.WSHandlerMethod;

@Component("client_webSocketHandlerExt")
@Scope("prototype")
public class WebSocketExtClientHandler extends TextWebSocketHandler {

	public boolean DEBUG = System.getProperty("lu.rescue_rush.ws_ext.debug", "false").equalsIgnoreCase("true");

	private final Logger LOGGER;

	private StandardWebSocketClient client;
	private WebSocketSession session;
	private WebSocketSessionData sessionData;

	private final boolean persistentConnection;
	private final String wsPath;
	private final WSExtClientHandler bean;
	private final Map<String, WSHandlerMethod> methods;

	private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();

	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	private final Object lockObj = new Object();
	private boolean connectionReady = false;

	@Autowired
	private ObjectMapper objectMapper;

	public WebSocketExtClientHandler(String wsPath, WSExtClientHandler bean, boolean persistentConnection,
			Map<String, WSHandlerMethod> methods) {
		this.wsPath = wsPath.isEmpty() ? null : wsPath;
		this.bean = bean;
		this.persistentConnection = persistentConnection;
		this.methods = methods.entrySet().stream().collect(Collectors.toMap(k -> normalizeURI(k.getKey()), v -> v.getValue()));

		LOGGER = Logger
				.getLogger("WebSocketHandler " + AopUtils.getTargetClass(bean.getClass().getSimpleName())
						+ (wsPath == null ? "" : " (" + wsPath + ")"));
	}

	@PostConstruct
	private void init() {
		bean.setWebSocketHandler(this);
		bean.init();
	}
	
	public void start() {
		connect();
	}

	public void scheduleStart() {
		reconnectScheduler.submit(this::connect);
	}

	private void connect() {
		if (session != null) {
			synchronized (session) {
				if (session != null || session.isOpen()) {
					System.out.println("connect stopped");
					return;
				}
			}
		}

		final URI path = wsPath == null ? bean.buildRemoteURI() : bean.buildRemoteURI().resolve(wsPath);

		try {
			client = new StandardWebSocketClient();
			session = client.execute(this, bean.buildHttpHeaders(), path).get();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Connection failed to '" + path + "': ", e);
			scheduleReconnect();
		}
	}

	private void scheduleReconnect() {
		if (persistentConnection) {
			if (DEBUG) {
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

		if (DEBUG) {
			LOGGER.info("Received message: " + message.getPayload());
		}

		final JsonNode incomingJson = objectMapper.readTree(message.getPayload());
		badRequest(!incomingJson.has("destination"), "Invalid packet format: missing 'destination' field.", message.getPayload());
		String requestPath = incomingJson.get("destination").asText();
		final String packetId = incomingJson.has("packetId") ? incomingJson.get("packetId").asText() : null;
		final JsonNode payload = incomingJson.get("payload");

		final WSHandlerMethod handlerMethod = resolveMethod(requestPath);
		badRequest(handlerMethod == null, "No method found for destination: " + requestPath, message.getPayload());
		final Method method = handlerMethod.method();
		badRequest(method == null, "No method attached for destination: " + requestPath, message.getPayload());
		final boolean returnsVoid = method.getReturnType().equals(Void.TYPE);
		requestPath = handlerMethod.inPath();
		badRequest(requestPath == null, "No request path defined for destination: " + requestPath, message.getPayload());
		final String responsePath = handlerMethod.outPath();

		sessionData.setRequestPath(requestPath);
		sessionData.setPacketId(packetId);
		sessionData.lastPacket = System.currentTimeMillis();

		Exception err = null;
		try {
			Object returnValue = null;

			if (method.getParameterCount() == 2) {
				badRequest(payload == null, "Payload expected for destination: " + requestPath, message.getPayload());
				final JavaType javaType = TypeFactory.defaultInstance().constructType(method.getGenericParameterTypes()[1]);
				final Object param = objectMapper.readValue(objectMapper.treeAsTokens(payload), javaType);
				
				returnValue = method.invoke(bean, sessionData, param);
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

				if (DEBUG) {
					LOGGER.info("Sending response (" + requestPath + " -> " + responsePath + "): " + jsonResponse);
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

			session.sendMessage(new TextMessage(root.toString()));
		}

		if (err != null) {
			throw err;
		}
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		setSession(session);

		if (DEBUG) {
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
		if (DEBUG)
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

			if (DEBUG) {
				LOGGER.info("Sending packet (" + destination + "): " + json);
			}
			
			session.sendMessage(new TextMessage(json));

			sessionData.lastPacket = System.currentTimeMillis();
		} catch (IOException e) {
			throw new RuntimeException("Exception caught while sending packet.", e);
		}
	}

	public void send(String destination, Object payload) {
		send(destination, null, payload);
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

		if (matchingPatterns.isEmpty()) {
			return null;
		}

		matchingPatterns.sort(pathMatcher.getPatternComparator(requestPath));
		String bestPattern = matchingPatterns.get(0);
		WSHandlerMethod bestMatch = methods.get(bestPattern);

		return bestMatch;
	}

	protected void setSession(WebSocketSession session) {
		if (this.session != session && DEBUG) {
			LOGGER.info("WebSocketSession instance changed for some reason.");
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
			if (DEBUG) {
				LOGGER.warning("Thread: " + Thread.currentThread().getName() + " interrupted while awaiting connection");
			}
			Thread.interrupted();
			return false;
		}
	}

	public void ready() {
		synchronized (lockObj) {
			connectionReady = true;
			lockObj.notifyAll();
		}
	}

	public void block() {
		synchronized (lockObj) {
			connectionReady = false;
		}
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

}