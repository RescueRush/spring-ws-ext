package lu.rescue_rush.spring.ws_ext.server.config;

import static lu.rescue_rush.spring.ws_ext.common.WSPathUtils.normalizeURI;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lu.rescue_rush.spring.ws_ext.common.annotations.WSMapping;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSResponseMapping;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerMappingRegistry;
import lu.rescue_rush.spring.ws_ext.server.WebSocketExtServerHandler;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerMappingRegistry.WSHandlerData;
import lu.rescue_rush.spring.ws_ext.server.WSExtServerMappingRegistry.WSHandlerMethod;
import lu.rescue_rush.spring.ws_ext.server.annotations.AllowAnonymous;
import lu.rescue_rush.spring.ws_ext.server.annotations.WSTimeout;

@Configuration
public class WSExtServerMappingScanner {

	private final Logger LOGGER = Logger.getLogger(WSExtServerMappingScanner.class.getName());

	@Autowired
	private ApplicationContext applicationContext;

	@Bean
	public WSExtServerMappingRegistry wsExtServerMappingRegistry() {
		final WSExtServerMappingRegistry registry = new WSExtServerMappingRegistry();

		for (Object bean : applicationContext.getBeansWithAnnotation(WSMapping.class).values()) {

			if (!(bean instanceof WSExtServerHandler)) {
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
				final AllowAnonymous allowAnonymous = targetMethod.getAnnotation(AllowAnonymous.class);

				final String inPath = normalizeURI(mapping.path());
				final String outPath = normalizeURI(responseMapping == null ? mapping.path() : responseMapping.path());
				final boolean allowAnonymousFlag = allowAnonymous != null;

				methods.put(mapping.path(), new WSHandlerMethod(proxyMethod, inPath, outPath, allowAnonymousFlag));
			}

			register(registry,
					beanMapping.path(),
					(WSExtServerHandler) bean,
					timeout != null ? timeout.value() : true,
					timeout != null ? timeout.timeout() : WebSocketExtServerHandler.TIMEOUT,
					methods);
		}

		return registry;
	}

	public void register(
			WSExtServerMappingRegistry registry,
			String path,
			WSExtServerHandler bean,
			boolean timeout,
			long timeoutDelayMs,
			Map<String, WSHandlerMethod> methods) {
		
		final WebSocketExtServerHandler attachedHandler = applicationContext
				.getBean(WebSocketExtServerHandler.class, path, bean, methods, timeout, timeoutDelayMs);
		applicationContext.getAutowireCapableBeanFactory().autowireBean(attachedHandler);
		
		bean.setWebSocketHandler(attachedHandler);
		registry.register(path, new WSHandlerData(path, bean, methods, attachedHandler));
	}

}