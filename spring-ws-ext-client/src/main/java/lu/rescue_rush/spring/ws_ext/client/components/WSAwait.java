package lu.rescue_rush.spring.ws_ext.client.components;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lu.kbra.pclib.pointer.ObjectPointer;
import lu.kbra.pclib.pointer.prim.IntPointer;
import lu.rescue_rush.spring.ws_ext.client.WSExtClientHandler.WebSocketSessionData;
import lu.rescue_rush.spring.ws_ext.client.components.abstr.GenericWSExtClientComponent;
import lu.rescue_rush.spring.ws_ext.client.components.abstr.TransactionAwareComponent;
import lu.rescue_rush.spring.ws_ext.common.MessageData;
import lu.rescue_rush.spring.ws_ext.common.MessageData.TransactionDirection;

/**
 * Helper component for request/response style WebSocket flows.
 * <p>
 * {@code WSAwait} lets code wait for the next incoming message on a given
 * destination. Call one of the {@code await(...)} methods before or while
 * sending a request. When an incoming transaction with the same destination is
 * received, the component stores the payload and releases the waiting thread.
 * </p>
 * <p>
 * Outgoing transactions are ignored. If no matching incoming transaction arrives
 * before the timeout, {@code await(...)} returns {@code null}. The default timeout
 * is {@link #DEFAULT_ANSWER_TIMEOUT_MS} and can be changed with
 * {@link #setDefaultAnswerTimeoutMs(long)}.
 * </p>
 * <p>
 * The component is prototype-scoped. Do not use one shared instance for unrelated
 * sessions unless that is intended.
 * </p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WSAwait extends GenericWSExtClientComponent implements TransactionAwareComponent {

	public static final long DEFAULT_ANSWER_TIMEOUT_MS = 2_000; // 2s

	protected final Map<String, ClearingIntPointer> awaiting = new ConcurrentHashMap<>();
	protected final Map<String, ObjectPointer> got = new ConcurrentHashMap<>();

	protected long answerTimeoutMs = DEFAULT_ANSWER_TIMEOUT_MS;

	@Override
	public void onTransaction(
			final WebSocketSessionData sessionData,
			final TransactionDirection dir,
			final MessageData in,
			final MessageData out) {
		if (dir == TransactionDirection.OUT) {
			return;
		}

		this.awaitGot(in.destination(), in.payload());
	}

	/**
	 * Waits for the next incoming payload on {@code endpoint} using the default
	 * timeout.
	 *
	 * @param endpoint destination to listen for
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public synchronized <T> T await(final String endpoint) {
		return this.await(endpoint, answerTimeoutMs, () -> {
		});
	}

	/**
	 * Waits for the next incoming payload on {@code endpoint}.
	 *
	 * @param endpoint destination to listen for
	 * @param timeout maximum wait time in milliseconds
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public <T> T await(final String endpoint, final long timeout) {
		return this.await(endpoint, timeout, () -> {
		});
	}

	/**
	 * Runs {@code sendAction} after the endpoint has been registered, then waits for
	 * the next incoming payload on {@code endpoint} using the default timeout.
	 *
	 * @param endpoint destination to listen for
	 * @param sendAction action that usually sends the request message
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public synchronized <T> T await(final String endpoint, final Runnable sendAction) {
		return this.await(endpoint, answerTimeoutMs, sendAction);
	}

	/**
	 * Runs {@code sendAction} after the endpoint has been registered, then waits for
	 * the next incoming payload on {@code endpoint}. Registering first avoids losing
	 * fast responses that arrive while the request is being sent.
	 *
	 * @param endpoint destination to listen for
	 * @param timeout maximum wait time in milliseconds
	 * @param sendAction action that usually sends the request message
	 * @return the received payload, or {@code null} when the timeout expires
	 */
	public synchronized <T> T await(final String endpoint, final long timeout, final Runnable sendAction) {
		this.awaiting.putIfAbsent(endpoint, new ClearingIntPointer(endpoint));
		this.awaiting.get(endpoint).increment();
		this.got.putIfAbsent(endpoint, new ObjectPointer<>());
		sendAction.run();
		this.got.get(endpoint).waitForSet(timeout);
		final Object gotObj = this.got.get(endpoint).get();
		this.awaiting.get(endpoint).decrement();
		return (T) gotObj;
	}

	private <T> void awaitGot(final String destination, final T payload) {
		if (this.got.containsKey(destination)) {
			this.got.get(destination).set(payload);
		}
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

	protected class ClearingIntPointer extends IntPointer {

		private final String key;

		public ClearingIntPointer(final String key) {
			this.key = key;
		}

		public ClearingIntPointer(final int value, final String key) {
			super(value);
			this.key = key;
		}

		@Override
		public synchronized IntPointer set(final Integer value) {
			if (value == 0) {
				WSAwait.this.got.remove(this.key);
			}
			return super.set(value);
		}

	}

}