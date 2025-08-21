package lu.rescue_rush.spring.ws_ext.server;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.springframework.web.socket.WebSocketSession;

import lu.rescue_rush.spring.ws_ext.server.WebSocketExtServerHandler.ScheduledTaskData;
import lu.rescue_rush.spring.ws_ext.server.WebSocketExtServerHandler.WebSocketSessionData;

public abstract class WSExtServerHandler {

	private WebSocketExtServerHandler webSocketHandler;

	public void init() {
	}

	public void onConnect(WebSocketSessionData sessionData) {
	}

	public void onDisconnect(WebSocketSessionData sessionData) {
	}

	public void send(WebSocketSessionData sessionData, String destination, String packetId, Object payload) {
		webSocketHandler.send(sessionData, destination, packetId, payload);
	}

	public void send(WebSocketSessionData sessionData, String destination, Object payload) {
		webSocketHandler.send(sessionData, destination, payload);
	}

	public void send(WebSocketSession session, String destination, String packetId, Object payload) {
		webSocketHandler.send(session, destination, packetId, payload);
	}

	public void send(WebSocketSession session, String destination, Object payload) {
		webSocketHandler.send(session, destination, payload);
	}

	public void cancelScheduledTasks(WebSocketSession session) {
		webSocketHandler.cancelScheduledTasks(session);
	}

	public void cancelScheduledTasks(WebSocketSessionData sessionData) {
		webSocketHandler.cancelScheduledTasks(sessionData);
	}

	public void clearScheduledTasks(WebSocketSession session, String matchingId) {
		webSocketHandler.clearScheduledTasks(session, matchingId);
	}

	public void clearScheduledTasks(WebSocketSessionData sessionData, String matchingId) {
		webSocketHandler.clearScheduledTasks(sessionData, matchingId);
	}

	public void clearScheduledTasks(WebSocketSession session, Predicate<ScheduledTaskData<?>> pred) {
		webSocketHandler.clearScheduledTasks(session, pred);
	}

	public void clearScheduledTasks(WebSocketSessionData sessionData, Predicate<ScheduledTaskData<?>> pred) {
		webSocketHandler.clearScheduledTasks(sessionData, pred);
	}

	public Collection<ScheduledTaskData<?>> getScheduledTasks(WebSocketSession session) {
		return webSocketHandler.getScheduledTasks(session);
	}

	public Collection<ScheduledTaskData<?>> getScheduledTasks(WebSocketSessionData sessionData) {
		return webSocketHandler.getScheduledTasks(sessionData);
	}

	public <T> void scheduleTask(WebSocketSessionData sessionData, Runnable run, String id, long delay, TimeUnit unit) {
		webSocketHandler.scheduleTask(sessionData, run, id, delay, unit);
	}

	public <T> void scheduleTask(WebSocketSessionData sessionData, Callable<T> run, String id, long delay, TimeUnit unit) {
		webSocketHandler.scheduleTask(sessionData, run, id, delay, unit);
	}

	public <T> void scheduleTask(WebSocketSession session, Runnable run, String id, long delay, TimeUnit unit) {
		webSocketHandler.scheduleTask(session, run, id, delay, unit);
	}

	public <T> void scheduleTask(WebSocketSession session, Callable<T> run, String id, long delay, TimeUnit unit) {
		webSocketHandler.scheduleTask(session, run, id, delay, unit);
	}

	public WebSocketExtServerHandler getWebSocketHandler() {
		return webSocketHandler;
	}

	public void setWebSocketHandler(WebSocketExtServerHandler webSocketHandler) {
		this.webSocketHandler = webSocketHandler;
	}

	public WebSocketSessionData getUserSession(long userId) {
		return webSocketHandler.getUserSession(userId);
	}
	
	public List<WebSocketSessionData> getUserSessions(Set<Long> ids){
		return webSocketHandler.getUserSessions(ids);
	}

}
