package lu.rescue_rush.spring.ws_ext.client.components;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import lu.kbra.pclib.pointer.ObjectPointer;
import lu.rescue_rush.spring.ws_ext.common.MessageData;
import lu.rescue_rush.spring.ws_ext.common.MessageData.TransactionDirection;

class WSAwaitTest {

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public class WSAwaitDiff extends WSAwait {
		public Map<String, ClearingIntPointer> getAwaiting() {
			return this.awaiting;
		}

		public Map<String, ObjectPointer> getGot() {
			return this.got;
		}
	}

	@AfterEach
	void shutdownExecutor() {
		this.executor.shutdownNow();
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
			endpointWasRegistered.set(await.getGot().containsKey("/answer"));
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
	void awaitRemovesStoredPayloadAfterWaiterFinishes() {
		final WSAwaitDiff await = new WSAwaitDiff();

		await.await("/answer",
				1_000,
				() -> await.onTransaction(null, TransactionDirection.IN, new MessageData("/answer", "packet-1", "response"), null));

		Assertions.assertThat(await.getGot().containsKey("/answer")).isFalse();
	}

	@Test
	void defaultAnswerTimeoutCanBeChanged() {
		final WSAwaitDiff await = new WSAwaitDiff();

		await.setDefaultAnswerTimeoutMs(123);

		Assertions.assertThat(await.getDefaultAnswerTimeoutMs()).isEqualTo(123);
	}

}
