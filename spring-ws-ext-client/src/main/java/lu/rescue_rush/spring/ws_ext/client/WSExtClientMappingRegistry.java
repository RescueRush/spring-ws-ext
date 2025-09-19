package lu.rescue_rush.spring.ws_ext.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WSExtClientMappingRegistry {

	private final Map<String, WSExtClientHandler> beanMap = new ConcurrentHashMap<>();

	public String[] getAllBeans() {
		return beanMap.keySet().toArray(new String[0]);
	}

	public Map<String, WSExtClientHandler> getBeans() {
		return beanMap;
	}

	public WSExtClientHandler getBean(String name) {
		return beanMap.get(name);
	}

	public boolean hasBean(String path) {
		return beanMap.containsKey(path);
	}

	public void register(WSExtClientHandler WSExtClientHandler) {
		final String path = WSExtClientHandler.getWsPath();
		if (beanMap.containsKey(path)) {
			throw new IllegalArgumentException("WebSocket handler already registered for path: " + path
					+ ", conflicts between: " + WSExtClientHandler.getClass().getName() + " and "
					+ beanMap.get(path).getClass().getName());
		}
		beanMap.put(path, WSExtClientHandler);
	}

}
