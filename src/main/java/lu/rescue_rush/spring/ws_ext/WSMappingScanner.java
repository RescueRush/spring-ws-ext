package lu.rescue_rush.spring.ws_ext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import lu.rescue_rush.spring.ws_ext.WSMappingRegistry.WSHandlerMethod;

@Component
public class WSMappingScanner implements ApplicationContextAware {

	private final Logger LOGGER = Logger.getLogger(WSMappingScanner.class.getName());

	@Autowired
	private WSMappingRegistry registry;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		for (Object bean : applicationContext.getBeansWithAnnotation(WSMapping.class).values()) {

			if (!(bean instanceof WSExtHandler)) {
				LOGGER.warning("Bean " + bean.getClass().getName() + " is not a WSHandler. Skipping.");
				continue;
			}

			final Class<?> target = AopProxyUtils.ultimateTargetClass(bean);
			final WSTimeout timeout = target.getAnnotation(WSTimeout.class);

			if (!target.isAnnotationPresent(WSMapping.class)) {
				continue;
			}

			final WSMapping beanMapping = target.getAnnotation(WSMapping.class);

			final Map<String, WSHandlerMethod> methods = new ConcurrentHashMap<>();

			for (Method proxyMethod : bean.getClass().getDeclaredMethods()) {
				Method targetMethod = null;
				try {
					targetMethod = target.getDeclaredMethod(proxyMethod.getName(), proxyMethod.getParameterTypes());
				} catch (NoSuchMethodException e) {
					// LOGGER.warning("Proxy method `" + proxyMethod.getName() + "` not found in
					// target class. Skipping.");
					continue;
				} catch (SecurityException se) {
					LOGGER.warning("Security exception while accessing method `" + proxyMethod.getName() + "` in target class. Skipping.");
					continue;
				}

				if (!targetMethod.isAnnotationPresent(WSMapping.class)) {
					continue;
				}

				final WSMapping mapping = targetMethod.getAnnotation(WSMapping.class);
				final WSResponseMapping responseMapping = targetMethod.getAnnotation(WSResponseMapping.class);
				final WSAllowAnonymous allowAnonymous = targetMethod.getAnnotation(WSAllowAnonymous.class);

				final String inPath = normalizeURI(mapping.path());
				final String outPath = normalizeURI(responseMapping == null ? mapping.path() : responseMapping.path());
				final boolean allowAnonymousFlag = allowAnonymous != null;

				methods.put(mapping.path(), new WSHandlerMethod(proxyMethod, inPath, outPath, allowAnonymousFlag));
			}

			registry
					.register(beanMapping.path(),
							(WSExtHandler) bean,
							timeout != null ? timeout.value() : true,
							timeout != null ? timeout.timeout() : WebSocketHandlerExt.TIMEOUT,
							methods);
		}
	}

	public static String normalizeURI(String path) {
		if (path == null || path.isEmpty())
			return "/";
		String trimmed = path.replaceAll("^/+", "").replaceAll("/+$", "");
		return "/" + trimmed;
	}

}