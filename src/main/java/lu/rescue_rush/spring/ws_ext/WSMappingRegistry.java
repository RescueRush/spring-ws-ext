package lu.rescue_rush.spring.ws_ext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class WSMappingRegistry {

	@Autowired
	private ApplicationContext context;

	private final Map<String, WSHandlerData> beanMap = new ConcurrentHashMap<>();

	public void register(String path, WSExtHandler bean, boolean timeout, long timeoutDelayMs,
			Map<String, WSHandlerMethod> methods) {
		final WebSocketHandlerExt attachedHandler = context.getBean(WebSocketHandlerExt.class, path, bean, methods,
				timeout, timeoutDelayMs);
		bean.setWebSocketHandler(attachedHandler);
		beanMap.put(path, new WSHandlerData(path, bean, methods, attachedHandler));
	}

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

	public record WSHandlerData(String path, WSExtHandler bean, Map<String, WSHandlerMethod> methods,
			WebSocketHandlerExt handler) {

		public WSHandlerMethod getDestination(String dest) {
			return methods.get(dest);
		}

	}

	public boolean hasBean(String path) {
		return beanMap.containsKey(path);
	}

}
