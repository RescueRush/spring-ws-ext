package lu.rescue_rush.spring.ws_ext.server.component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lu.kbra.pclib.pointer.ObjectPointer;
import lu.rescue_rush.spring.ws_ext.common.MessageData;
import lu.rescue_rush.spring.ws_ext.common.MessageData.TransactionDirection;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;
import lu.rescue_rush.spring.ws_ext.server.component.abstr.GenericWSExtServerComponent;
import lu.rescue_rush.spring.ws_ext.server.component.abstr.TransactionAwareComponent;

/**
 * Helper component for request/response style WebSocket flows.
 * <p>
 * {@code WSAwait} lets server code wait for the next incoming message on a destination. Call one of
 * the {@code await(...)} methods before or while sending a request. When an incoming transaction
 * with the same destination is received, the component stores the payload and releases the waiting
 * thread.
 * </p>
 * <p>
 * By default, {@code await(endpoint, ...)} accepts the first matching message from any connected
 * WebSocket session. Use the session-specific overloads when the response must come from one client
 * only, from one of several client ids, or from a custom session predicate.
 * </p>
 * <p>
 * Outgoing transactions are ignored. If no matching incoming transaction arrives before the
 * timeout, {@code await(...)} returns {@code null}. The default timeout is
 * {@link #DEFAULT_ANSWER_TIMEOUT_MS} and can be changed with
 * {@link #setDefaultAnswerTimeoutMs(long)}.
 * </p>
 * <p>
 * The component is prototype-scoped. Do not use one shared instance for unrelated sessions unless
 * that is intended.
 * </p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WSAwait extends GenericWSExtServerComponent implements TransactionAwareComponent {

	public static final long DEFAULT_ANSWER_TIMEOUT_MS = 2_000; // 2s

	protected final Map<String, CopyOnWriteArrayList<AwaitRequest>> awaiting = new ConcurrentHashMap<>();

	protected long answerTimeoutMs = DEFAULT_ANSWER_TIMEOUT_MS;

	@Override
	public void onTransaction(
			final WebSocketSessionData sessionData,
			final TransactionDirection dir,
			final MessageData in,
			final MessageData out) {
		if (dir == TransactionDirection.OUT || in == null) {
			return;
		}

		this.awaitGot(sessionData, in.destination(), in.payload());
	}

	/**
	 * Waits for the next incoming payload on {@code endpoint} from any session using the default
	 * timeout.
	 *
	 * @param endpoint destination to listen for
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint) {
		return this.await(endpoint, answerTimeoutMs, () -> {
		});
	}

	/**
	 * Waits for the next incoming payload on {@code endpoint} from any session.
	 *
	 * @param endpoint destination to listen for
	 * @param timeout  maximum wait time in milliseconds
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final long timeout) {
		return this.await(endpoint, timeout, () -> {
		});
	}

	/**
	 * Runs {@code sendAction} after the endpoint has been registered, then waits for the next incoming
	 * payload on {@code endpoint} from any session using the default timeout.
	 *
	 * @param endpoint   destination to listen for
	 * @param sendAction action that usually sends the request message
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final Runnable sendAction) {
		return this.await(endpoint, answerTimeoutMs, sendAction);
	}

	/**
	 * Runs {@code sendAction} after the endpoint has been registered, then waits for the next incoming
	 * payload on {@code endpoint} from any session. Registering first avoids losing fast responses that
	 * arrive while the request is being sent.
	 *
	 * @param endpoint   destination to listen for
	 * @param timeout    maximum wait time in milliseconds
	 * @param sendAction action that usually sends the request message
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final long timeout, final Runnable sendAction) {
		return this.await(endpoint, timeout, anySession(), sendAction);
	}

	/**
	 * Waits for the next incoming payload on {@code endpoint} from the given session using the default
	 * timeout.
	 *
	 * @param endpoint    destination to listen for
	 * @param sessionData session that must send the message
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final WebSocketSessionData sessionData) {
		return this.await(endpoint, sessionData, answerTimeoutMs);
	}

	/**
	 * Waits for the next incoming payload on {@code endpoint} from the given session.
	 *
	 * @param endpoint    destination to listen for
	 * @param sessionData session that must send the message
	 * @param timeout     maximum wait time in milliseconds
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final WebSocketSessionData sessionData, final long timeout) {
		return this.await(endpoint, timeout, matchesSession(sessionData), () -> {
		});
	}

	/**
	 * Runs {@code sendAction} after the endpoint and session filter have been registered, then waits
	 * for a payload from the given session using the default timeout.
	 *
	 * @param endpoint    destination to listen for
	 * @param sessionData session that must send the message
	 * @param sendAction  action that usually sends the request message
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final WebSocketSessionData sessionData, final Runnable sendAction) {
		return this.await(endpoint, sessionData, answerTimeoutMs, sendAction);
	}

	/**
	 * Runs {@code sendAction} after the endpoint and session filter have been registered, then waits
	 * for a payload from the given session.
	 *
	 * @param endpoint    destination to listen for
	 * @param sessionData session that must send the message
	 * @param timeout     maximum wait time in milliseconds
	 * @param sendAction  action that usually sends the request message
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final WebSocketSessionData sessionData, final long timeout, final Runnable sendAction) {
		return this.await(endpoint, timeout, matchesSession(sessionData), sendAction);
	}

	/**
	 * Waits for the next incoming payload on {@code endpoint} from the session with the given
	 * application-level id using the default timeout.
	 *
	 * @param endpoint  destination to listen for
	 * @param sessionId application-level session id from {@link WebSocketSessionData#getId()}
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final String sessionId) {
		return this.await(endpoint, sessionId, answerTimeoutMs);
	}

	/**
	 * Waits for the next incoming payload on {@code endpoint} from the session with the given
	 * application-level id.
	 *
	 * @param endpoint  destination to listen for
	 * @param sessionId application-level session id from {@link WebSocketSessionData#getId()}
	 * @param timeout   maximum wait time in milliseconds
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final String sessionId, final long timeout) {
		return this.await(endpoint, timeout, matchesSessionId(sessionId), () -> {
		});
	}

	/**
	 * Runs {@code sendAction} after the endpoint and session id filter have been registered, then waits
	 * for a payload from that session.
	 *
	 * @param endpoint   destination to listen for
	 * @param sessionId  application-level session id from {@link WebSocketSessionData#getId()}
	 * @param timeout    maximum wait time in milliseconds
	 * @param sendAction action that usually sends the request message
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final String sessionId, final long timeout, final Runnable sendAction) {
		return this.await(endpoint, timeout, matchesSessionId(sessionId), sendAction);
	}

	/**
	 * Waits for the next incoming payload on {@code endpoint} from any session whose application-level
	 * id is part of {@code sessionIds}.
	 *
	 * @param endpoint   destination to listen for
	 * @param sessionIds allowed application-level session ids
	 * @param timeout    maximum wait time in milliseconds
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final Collection<String> sessionIds, final long timeout) {
		return this.await(endpoint, sessionIds, timeout, () -> {
		});
	}

	/**
	 * Runs {@code sendAction} after the endpoint and session id filter have been registered, then waits
	 * for the first payload from any allowed session.
	 *
	 * @param endpoint   destination to listen for
	 * @param sessionIds allowed application-level session ids
	 * @param timeout    maximum wait time in milliseconds
	 * @param sendAction action that usually sends the request message
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final Collection<String> sessionIds, final long timeout, final Runnable sendAction) {
		return this.await(endpoint, timeout, matchesAnySessionId(sessionIds), sendAction);
	}

	/**
	 * Waits for the next incoming payload on {@code endpoint} from the first session accepted by
	 * {@code sessionFilter}.
	 *
	 * @param endpoint      destination to listen for
	 * @param timeout       maximum wait time in milliseconds
	 * @param sessionFilter filter that selects allowed sessions
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final long timeout, final Predicate<WebSocketSessionData> sessionFilter) {
		return this.await(endpoint, timeout, sessionFilter, () -> {
		});
	}

	/**
	 * Runs {@code sendAction} after the endpoint and custom session filter have been registered, then
	 * waits for the first payload from a matching session.
	 *
	 * @param endpoint      destination to listen for
	 * @param timeout       maximum wait time in milliseconds
	 * @param sessionFilter filter that selects allowed sessions
	 * @param sendAction    action that usually sends the request message
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(
			final String endpoint,
			final long timeout,
			final Predicate<WebSocketSessionData> sessionFilter,
			final Runnable sendAction) {
		Objects.requireNonNull(endpoint, "endpoint");
		Objects.requireNonNull(sessionFilter, "sessionFilter");
		Objects.requireNonNull(sendAction, "sendAction");

		final AwaitRequest request = new AwaitRequest(sessionFilter);
		final CopyOnWriteArrayList<AwaitRequest> requests = this.awaiting.computeIfAbsent(endpoint, key -> new CopyOnWriteArrayList<>());
		requests.add(request);

		try {
			sendAction.run();
			request.payload.waitForSet(timeout);
			return (T) request.payload.get();
		} finally {
			requests.remove(request);
			if (requests.isEmpty()) {
				this.awaiting.remove(endpoint, requests);
			}
		}
	}

	private <T> void awaitGot(final WebSocketSessionData sessionData, final String destination, final T payload) {
		final List<AwaitRequest> requests = this.awaiting.get(destination);
		if (requests == null) {
			return;
		}

		for (AwaitRequest request : requests) {
			if (request.matches(sessionData)) {
				request.payload.set(payload);
			}
		}
	}

	private static Predicate<WebSocketSessionData> anySession() {
		return sessionData -> true;
	}

	private static Predicate<WebSocketSessionData> matchesSession(final WebSocketSessionData sessionData) {
		Objects.requireNonNull(sessionData, "sessionData");
		return current -> current == sessionData || (current != null && Objects.equals(current.getId(), sessionData.getId()));
	}

	private static Predicate<WebSocketSessionData> matchesSessionId(final String sessionId) {
		Objects.requireNonNull(sessionId, "sessionId");
		return current -> current != null && Objects.equals(current.getId(), sessionId);
	}

	private static Predicate<WebSocketSessionData> matchesAnySessionId(final Collection<String> sessionIds) {
		Objects.requireNonNull(sessionIds, "sessionIds");
		final Set<String> allowedIds = sessionIds.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
		return current -> current != null && allowedIds.contains(current.getId());
	}

	/**
	 * Sets the timeout used by overloads that do not receive an explicit timeout.
	 *
	 * @param answerTimeoutMs timeout in milliseconds
	 */
	public void setDefaultAnswerTimeoutMs(long answerTimeoutMs) {
		this.answerTimeoutMs = answerTimeoutMs;
	}

	/**
	 * @return the timeout used by overloads that do not receive an explicit timeout
	 */
	public long getDefaultAnswerTimeoutMs() {
		return answerTimeoutMs;
	}

	protected static class AwaitRequest {

		private final Predicate<WebSocketSessionData> sessionFilter;
		private final ObjectPointer payload = new ObjectPointer<>();

		private AwaitRequest(final Predicate<WebSocketSessionData> sessionFilter) {
			this.sessionFilter = sessionFilter;
		}

		private boolean matches(final WebSocketSessionData sessionData) {
			return sessionFilter.test(sessionData);
		}

	}

}
