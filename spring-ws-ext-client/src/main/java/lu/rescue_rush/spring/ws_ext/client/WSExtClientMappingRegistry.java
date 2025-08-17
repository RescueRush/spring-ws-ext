package lu.rescue_rush.spring.ws_ext.client;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WSExtClientMappingRegistry {

	private final Map<String, WSHandlerData> beanMap = new ConcurrentHashMap<>();

	public String[] getAllBeans() {
		return beanMap.keySet().toArray(new String[0]);
	}

	public Map<String, WSHandlerData> getBeans() {
		return beanMap;
	}

	public WSHandlerData getBeanData(String name) {
		return beanMap.get(name);
	}

	public WSExtClientHandler getBean(String name) {
		if (beanMap.containsKey(name)) {
			return beanMap.get(name).bean;
		}
		return null;
	}

	public record WSHandlerMethod(Method method, String inPath, String outPath) {
	}

	public record WSHandlerData(String path, WSExtClientHandler bean, Map<String, WSHandlerMethod> methods, WebSocketExtClientHandler handler, boolean persistentConnection) {

		public WSHandlerMethod getDestination(String dest) {
			return methods.get(dest);
		}

	}

	public boolean hasBean(String path) {
		return beanMap.containsKey(path);
	}

	public void register(String path, WSHandlerData wsHandlerData) {
		if (beanMap.containsKey(path)) {
			throw new IllegalArgumentException("WebSocket handler already registered for path: " + path + ", conflicts between: "
					+ wsHandlerData.getClass().getName() + " and " + beanMap.get(path).getClass().getName());
		}
		beanMap.put(path, wsHandlerData);
	}

}
