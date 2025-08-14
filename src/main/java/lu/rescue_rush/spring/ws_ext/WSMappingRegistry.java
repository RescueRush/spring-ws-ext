package lu.rescue_rush.spring.ws_ext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WSMappingRegistry {

	private final Map<String, WSHandlerData> beanMap = new ConcurrentHashMap<>();

	public String[] getAllBeans() {
		return beanMap.keySet().toArray(new String[0]);
	}

	public Map<String, WSHandlerData> getBeans() {
		return beanMap;
	}

	public WSHandlerData getBeanData(String path) {
		return beanMap.get(path);
	}

	public WSExtHandler getBean(String path) {
		if (beanMap.containsKey(path)) {
			return beanMap.get(path).bean;
		}
		return null;
	}

	public record WSHandlerMethod(Method method, String inPath, String outPath, boolean allowAnonymous) {
	}

	public record WSHandlerData(String path, WSExtHandler bean, Map<String, WSHandlerMethod> methods, WebSocketHandlerExt handler) {

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
