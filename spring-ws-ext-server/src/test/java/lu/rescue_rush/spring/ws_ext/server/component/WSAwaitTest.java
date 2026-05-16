package lu.rescue_rush.spring.ws_ext.server.component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import lu.rescue_rush.spring.ws_ext.common.MessageData;
import lu.rescue_rush.spring.ws_ext.common.MessageData.TransactionDirection;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSMapping;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

class WSAwaitTest {

	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final TestServerHandler handler = new TestServerHandler();

	public class WSAwaitDiff extends WSAwait {
		public Map<String, CopyOnWriteArrayList<AwaitRequest>> getAwaiting() {
			return super.awaiting;
		}
	}

	@AfterEach
	void shutdownExecutor() {
		this.executor.shutdownNow();
		this.handler.shutdown();
	}

	@Test
	void awaitReturnsPayloadFromMatchingIncomingTransaction() throws Exception {
		final WSAwaitDiff await = new WSAwaitDiff();
		final CountDownLatch waitingRegistered = new CountDownLatch(1);

		final Future<String> result = this.executor.submit(() -> await.await("/answer", 1_000, waitingRegistered::countDown));

		Assertions.assertThat(waitingRegistered.await(500, TimeUnit.MILLISECONDS)).isTrue();

		await.onTransaction(null, TransactionDirection.IN, new MessageData("/answer", "packet-1", "ok"), null);

		Assertions.assertThat(result.get(500, TimeUnit.MILLISECONDS)).isEqualTo("ok");
	}

	@Test
	void awaitRunsSendActionAfterEndpointIsRegistered() throws Exception {
		final WSAwaitDiff await = new WSAwaitDiff();
		final AtomicBoolean endpointWasRegistered = new AtomicBoolean(false);

		final String result = await.await("/answer", 1_000, () -> {
			endpointWasRegistered.set(await.getAwaiting().containsKey("/answer"));
			await.onTransaction(null, TransactionDirection.IN, new MessageData("/answer", "packet-1", "response"), null);
		});

		Assertions.assertThat(endpointWasRegistered).isTrue();
		Assertions.assertThat(result).isEqualTo("response");
	}

	@Test
	void awaitIgnoresOutgoingTransactions() throws Exception {
		final WSAwaitDiff await = new WSAwaitDiff();
		final CountDownLatch waitingRegistered = new CountDownLatch(1);

		final Future<String> result = this.executor.submit(() -> await.await("/answer", 1_000, waitingRegistered::countDown));

		Assertions.assertThat(waitingRegistered.await(500, TimeUnit.MILLISECONDS)).isTrue();

		await.onTransaction(null, TransactionDirection.OUT, new MessageData("/answer", "packet-1", "wrong"), null);
		Thread.sleep(100);
		Assertions.assertThat(result).isNotDone();

		await.onTransaction(null, TransactionDirection.IN, new MessageData("/answer", "packet-2", "right"), null);

		Assertions.assertThat(result.get(500, TimeUnit.MILLISECONDS)).isEqualTo("right");
	}

	@Test
	void awaitReturnsNullWhenTimeoutExpires() {
		final WSAwaitDiff await = new WSAwaitDiff();

		final Object result = await.await("/missing", 20);

		Assertions.assertThat(result).isNull();
	}

	@Test
	void awaitRemovesAwaiterAfterWaiterFinishes() {
		final WSAwaitDiff await = new WSAwaitDiff();

		await.await("/answer",
				1_000,
				() -> await.onTransaction(null, TransactionDirection.IN, new MessageData("/answer", "packet-1", "response"), null));

		Assertions.assertThat(await.getAwaiting().containsKey("/answer")).isFalse();
	}

	@Test
	void awaitForSpecificSessionIgnoresPayloadsFromOtherSessions() throws Exception {
		final WSAwaitDiff await = new WSAwaitDiff();
		final WebSocketSessionData wantedSession = this.session("wanted");
		final WebSocketSessionData otherSession = this.session("other");
		final CountDownLatch waitingRegistered = new CountDownLatch(1);

		final Future<String> result = this.executor
				.submit(() -> await.await("/answer", wantedSession, 1_000, waitingRegistered::countDown));

		Assertions.assertThat(waitingRegistered.await(500, TimeUnit.MILLISECONDS)).isTrue();

		await.onTransaction(otherSession, TransactionDirection.IN, new MessageData("/answer", "packet-1", "wrong"), null);
		Thread.sleep(100);
		Assertions.assertThat(result).isNotDone();

		await.onTransaction(wantedSession, TransactionDirection.IN, new MessageData("/answer", "packet-2", "right"), null);

		Assertions.assertThat(result.get(500, TimeUnit.MILLISECONDS)).isEqualTo("right");
	}

	@Test
	void awaitForSessionIdAcceptsMatchingSessionId() throws Exception {
		final WSAwaitDiff await = new WSAwaitDiff();
		final WebSocketSessionData wantedSession = this.session("wanted");

		final String result = await.await("/answer",
				"wanted",
				1_000,
				() -> await
						.onTransaction(wantedSession, TransactionDirection.IN, new MessageData("/answer", "packet-1", "response"), null));

		Assertions.assertThat(result).isEqualTo("response");
	}

	@Test
	void awaitForSessionIdsAcceptsAnyAllowedSessionId() throws Exception {
		final WSAwaitDiff await = new WSAwaitDiff();
		final WebSocketSessionData allowedSession = this.session("allowed-2");

		final String result = await.await("/answer",
				List.of("allowed-1", "allowed-2"),
				1_000,
				() -> await
						.onTransaction(allowedSession, TransactionDirection.IN, new MessageData("/answer", "packet-1", "response"), null));

		Assertions.assertThat(result).isEqualTo("response");
	}

	@Test
	void awaitWithCustomSessionFilterAcceptsMatchingSession() throws Exception {
		final WSAwaitDiff await = new WSAwaitDiff();
		final WebSocketSessionData matchingSession = this.session("user-admin");

		final String result = await.await("/answer",
				1_000,
				session -> session.getId().startsWith("user-"),
				() -> await
						.onTransaction(matchingSession, TransactionDirection.IN, new MessageData("/answer", "packet-1", "response"), null));

		Assertions.assertThat(result).isEqualTo("response");
	}

	@Test
	void defaultAnswerTimeoutCanBeChanged() {
		final WSAwaitDiff await = new WSAwaitDiff();

		await.setDefaultAnswerTimeoutMs(123);

		Assertions.assertThat(await.getDefaultAnswerTimeoutMs()).isEqualTo(123);
	}

	private WebSocketSessionData session(final String id) {
		return this.handler.new WebSocketSessionData(id);
	}

	@WSMapping(path = "/test")
	private static class TestServerHandler extends WSExtServerHandler {

		private void shutdown() {
			this.executorService.shutdownNow();
		}

	}

}
