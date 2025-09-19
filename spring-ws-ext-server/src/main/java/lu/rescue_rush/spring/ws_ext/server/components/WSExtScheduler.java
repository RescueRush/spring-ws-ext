package lu.rescue_rush.spring.ws_ext.server.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler.WebSocketSessionData;

@Component
@Scope("prototype")
public class WSExtScheduler extends GenericWSExtComponent {

	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

	private final Map<String, List<ScheduledTaskData<?>>> scheduledTasks = new ConcurrentHashMap<>();

	@Override
	public void init() {
		executorService.scheduleAtFixedRate(() -> {
			// clear executed/cancelled scheduled tasks
			scheduledTasks.entrySet().forEach(e -> e.getValue().removeIf(t -> t.isCancelled() || t.isDone()));
			scheduledTasks.entrySet().removeIf(e -> e.getValue().isEmpty());
		}, WSExtServerHandler.PERIODIC_CHECK_DELAY, WSExtServerHandler.PERIODIC_CHECK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void onDisconnect(WebSocketSessionData sessionData) {
		clearScheduledTasks(sessionData);
	}

	public boolean cancelScheduledTasks(WebSocketSessionData sessionData) {
		final String sessionId = sessionData.getId();
		if (!scheduledTasks.containsKey(sessionId)) {
			return false;
		}
		scheduledTasks.get(sessionId).forEach(ScheduledTaskData::cancel);
		return !scheduledTasks.get(sessionId).isEmpty();
	}

	public boolean clearScheduledTasks(WebSocketSessionData sessionData) {
		Objects.requireNonNull(sessionData);
		if (!scheduledTasks.containsKey(sessionData.getId())) {
			return false;
		}
		scheduledTasks.get(sessionData.getId()).stream().forEach(t -> t.cancel());
		final boolean removed = scheduledTasks.get(sessionData.getId()).size() > 0;
		scheduledTasks.get(sessionData.getId()).clear();
		scheduledTasks.remove(sessionData.getId());
		return removed;
	}

	public boolean clearScheduledTasks(WebSocketSessionData sessionData, String matchingId) {
		Objects.requireNonNull(matchingId);
		return clearScheduledTasks(sessionData, (s) -> matchingId.equals(s.getId()));
	}

	public boolean clearScheduledTasks(WebSocketSessionData sessionData, Predicate<ScheduledTaskData<?>> pred) {
		Objects.requireNonNull(sessionData);
		Objects.requireNonNull(pred);
		if (!scheduledTasks.containsKey(sessionData.getId())) {
			return false;
		}
		scheduledTasks.get(sessionData.getId()).stream().filter(pred).forEach(t -> t.cancel());
		final boolean removed = scheduledTasks.get(sessionData.getId()).removeIf(pred);
		return removed;
	}

	public Collection<ScheduledTaskData<?>> getScheduledTasks(WebSocketSessionData sessionData) {
		return scheduledTasks.get(sessionData.getId());
	}

	public <T> void scheduleTask(WebSocketSessionData sessionData, Runnable run, String id, long delay, TimeUnit unit) {
		Objects.requireNonNull(sessionData);
		Objects.requireNonNull(run);
		Objects.requireNonNull(id);
		scheduleTask(sessionData, (Callable<Void>) () -> {
			run.run();
			return null;
		}, id, delay, unit);
	}

	public <T> void scheduleTask(WebSocketSessionData sessionData, Callable<T> run, String id, long delay,
			TimeUnit unit) {
		Objects.requireNonNull(sessionData);
		
		final ScheduledFuture<T> newTask = executorService.schedule(run, delay, unit);

		scheduledTasks.computeIfAbsent(sessionData.getId(), (k) -> Collections.synchronizedList(new ArrayList<>()));
		scheduledTasks.get(sessionData.getId()).add(new ScheduledTaskData<T>(id, newTask));
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

}
