package lu.rescue_rush.spring.ws_ext.client.config;

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
import org.springframework.util.StringUtils;

import lu.rescue_rush.spring.ws_ext.client.WSExtClientHandler;
import lu.rescue_rush.spring.ws_ext.client.WSExtClientMappingRegistry;
import lu.rescue_rush.spring.ws_ext.client.WSExtClientMappingRegistry.WSHandlerData;
import lu.rescue_rush.spring.ws_ext.client.WSExtClientMappingRegistry.WSHandlerMethod;
import lu.rescue_rush.spring.ws_ext.client.WebSocketExtClientHandler;
import lu.rescue_rush.spring.ws_ext.client.annotations.WSPersistentConnection;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSMapping;
import lu.rescue_rush.spring.ws_ext.common.annotations.WSResponseMapping;

@Configuration
public class WSExtClientMappingScanner {

	private final Logger LOGGER = Logger.getLogger(WSExtClientMappingScanner.class.getName());

	@Autowired
	private ApplicationContext applicationContext;

	@Bean
	public WSExtClientMappingRegistry wsExtClientMappingRegistry() {
		final WSExtClientMappingRegistry registry = new WSExtClientMappingRegistry();

		for (Object bean : applicationContext.getBeansWithAnnotation(WSMapping.class).values()) {

			if (!(bean instanceof WSExtClientHandler)) {
				LOGGER.warning("Bean " + bean.getClass().getName() + " is not a WSHandler. Skipping.");
				continue;
			}

			final Class<?> target = AopProxyUtils.ultimateTargetClass(bean);

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

				final String inPath = normalizeURI(mapping.path());
				final String outPath = normalizeURI(responseMapping == null ? mapping.path() : responseMapping.path());

				methods.put(mapping.path(), new WSHandlerMethod(proxyMethod, inPath, outPath));
			}

			final WSPersistentConnection persistentConnection = target.getAnnotation(WSPersistentConnection.class);
			final boolean persistentConnectionFlag = persistentConnection == null ? false : persistentConnection.value();

			register(registry, beanMapping.path(), (WSExtClientHandler) bean, methods, persistentConnectionFlag);
		}

		return registry;
	}

	public void register(
			WSExtClientMappingRegistry registry,
			String path,
			WSExtClientHandler bean,
			Map<String, WSHandlerMethod> methods,
			boolean persistentConnectionFlag) {

		final String name = AopProxyUtils.ultimateTargetClass(bean).getSimpleName();

		// init bean manager
		final WebSocketExtClientHandler attachedHandler = applicationContext
				.getBean(WebSocketExtClientHandler.class, path, bean, persistentConnectionFlag, methods);
		applicationContext.getAutowireCapableBeanFactory().autowireBean(attachedHandler);
		applicationContext.getAutowireCapableBeanFactory().initializeBean(bean, StringUtils.uncapitalize(name));

		// init bean
		bean.setWebSocketHandler(attachedHandler);
		bean.init();

		registry.register(name, new WSHandlerData(path, bean, methods, attachedHandler, persistentConnectionFlag));
	}

}