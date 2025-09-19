package lu.rescue_rush.spring.ws_ext.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WSExtServerMappingRegistry {

	private final Map<String, WSExtServerHandler> beanMap = new ConcurrentHashMap<>();

	public String[] getAllBeans() {
		return beanMap.keySet().toArray(new String[0]);
	}

	public Map<String, WSExtServerHandler> getBeans() {
		return beanMap;
	}

	public WSExtServerHandler getBean(String path) {
		return beanMap.get(path);
	}

	public boolean hasBean(String path) {
		return beanMap.containsKey(path);
	}

	public void register(WSExtServerHandler wsExtServerHandler) {
		final String path = wsExtServerHandler.getBeanPath();
		if (beanMap.containsKey(path)) {
			throw new IllegalArgumentException("WebSocket handler already registered for path: " + path
					+ ", conflicts between: " + wsExtServerHandler.getClass().getName() + " and "
					+ beanMap.get(path).getClass().getName());
		}
		beanMap.put(path, wsExtServerHandler);
	}

}
